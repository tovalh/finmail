package com.tovalh.finmail.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.tovalh.finmail.model.Categoria;
import com.tovalh.finmail.model.Comercio;
import com.tovalh.finmail.model.Transaccion;
import com.tovalh.finmail.parser.CorreoBanco;
import com.tovalh.finmail.parser.MovimientoBancario;
import com.tovalh.finmail.parser.ParserCorreoBanco;
import com.tovalh.finmail.repository.CategoriaRepository;
import com.tovalh.finmail.repository.ComercioRepository;
import com.tovalh.finmail.repository.TransaccionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TransaccionService {

    private static final Logger log = LoggerFactory.getLogger(TransaccionService.class);

    private final GmailService gmailService;
    private final ClaudeService claudeService;
    private final TransaccionRepository transaccionRepository;
    private final ComercioRepository comercioRepository;
    private final CategoriaRepository categoriaRepository;
    private final List<ParserCorreoBanco> parsers;

    public TransaccionService(GmailService gmailService,
                              ClaudeService claudeService,
                              TransaccionRepository transaccionRepository,
                              ComercioRepository comercioRepository,
                              CategoriaRepository categoriaRepository,
                              List<ParserCorreoBanco> parsers) {
        this.gmailService = gmailService;
        this.claudeService = claudeService;
        this.transaccionRepository = transaccionRepository;
        this.comercioRepository = comercioRepository;
        this.categoriaRepository = categoriaRepository;
        this.parsers = parsers;
    }

    @Scheduled(fixedDelay = 300000)
    public void procesarCorreosNuevos() {
        log.info("Revisando correos nuevos");
        try {
            for (Message msg : gmailService.fetchBankMessages()) {
                if (!transaccionRepository.existsByGmailMessageId(msg.getId())) {
                    procesarMensaje(msg, true);
                }
            }
        } catch (Exception e) {
            log.error("Error al leer correos de Gmail", e);
        }
    }

    /**
     * Importa todos los correos desde una fecha. Para no hacer una llamada a Claude
     * por correo, las categorías se sugieren al final en un solo lote.
     */
    public int cargarHistorico(String afterDate) {
        log.info("Carga histórica desde {}", afterDate);
        List<Message> mensajes;
        try {
            mensajes = gmailService.fetchAllBankMessagesSince(afterDate);
        } catch (Exception e) {
            log.error("Error al consultar Gmail en la carga histórica", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudieron leer los correos desde Gmail. Revisa la conexión e intenta de nuevo.");
        }

        int guardados = 0;
        for (Message msg : mensajes) {
            if (!transaccionRepository.existsByGmailMessageId(msg.getId()) && procesarMensaje(msg, false) != null) {
                guardados++;
            }
        }

        log.info("Carga histórica: {} correos revisados, {} movimientos nuevos", mensajes.size(), guardados);
        categorizarComerciosNuevosBatch();
        return guardados;
    }

    @Transactional
    public void categorizarComerciosNuevosBatch() {
        List<String> categorias = nombresDeCategorias();
        if (categorias.isEmpty()) return;

        List<Comercio> sinCategoria = comercioRepository.findAll().stream()
                .filter(c -> c.getCategoria() == null)
                .toList();
        if (sinCategoria.isEmpty()) return;

        List<String> nombres = sinCategoria.stream().map(Comercio::getNombre).toList();
        log.info("Pidiendo a Claude categorías para {} comercios nuevos", nombres.size());
        Map<String, String> sugerencias = claudeService.sugerirCategoriasBatch(nombres, categorias);

        for (Comercio comercio : sinCategoria) {
            String sugerida = sugerencias.get(comercio.getNombre());
            if (sugerida == null) continue;
            categoriaRepository.findByNombre(sugerida).ifPresent(categoria ->
                    transaccionRepository.findByComercioIdAndCategoriaSugeridaIsNullAndEliminadoFalse(comercio.getId())
                            .forEach(t -> {
                                t.setCategoriaSugerida(categoria);
                                transaccionRepository.save(t);
                            }));
        }
    }

    private Transaccion procesarMensaje(Message msg, boolean sugerirCategoria) {
        try {
            Transaccion transaccion = guardarMovimiento(msg);
            if (transaccion != null && sugerirCategoria) asignarCategoriaSugerida(transaccion);
            return transaccion;
        } catch (Exception e) {
            log.error("Error al procesar el correo {}", msg.getId(), e);
            return null;
        }
    }

    private Transaccion guardarMovimiento(Message msg) {
        String remitente = extraerHeader(msg, "From");
        Optional<ParserCorreoBanco> parser = parsers.stream().filter(p -> p.acepta(remitente)).findFirst();
        if (parser.isEmpty()) {
            log.warn("No hay parser para el remitente {} (correo {})", remitente, msg.getId());
            return null;
        }

        CorreoBanco correo = new CorreoBanco(remitente, msg.getSnippet(), extraerTextoHtml(msg));
        Optional<MovimientoBancario> leido = parser.get().parsear(correo);
        if (leido.isEmpty()) {
            log.warn("El correo {} de {} no tiene el formato esperado: {}", msg.getId(), remitente, msg.getSnippet());
            return null;
        }

        MovimientoBancario mov = leido.get();
        Transaccion transaccion = transaccionRepository.save(Transaccion.builder()
                .gmailMessageId(msg.getId())
                .tipoTransaccion(mov.tipo())
                .monto(mov.monto())
                .fecha(mov.fecha())
                .emisorEmail(remitente)
                .numeroCuenta(mov.numeroCuenta())
                .destinatario(mov.destinatario())
                .mensaje(mov.mensaje())
                .comercio(buscarOCrearComercio(mov.comercio()))
                .build());

        log.info("{} guardado: {} ${} en {}", mov.tipo(), mov.fecha(), mov.monto(), mov.comercio());
        return transaccion;
    }

    // Si el comercio ya tiene categoría aprendida se usa esa y no se consulta a Claude.
    private void asignarCategoriaSugerida(Transaccion transaccion) {
        Comercio comercio = transaccion.getComercio();

        if (comercio.getCategoria() != null) {
            transaccion.setCategoriaSugerida(comercio.getCategoria());
            transaccionRepository.save(transaccion);
            return;
        }

        List<String> categorias = nombresDeCategorias();
        if (categorias.isEmpty()) {
            log.warn("No hay categorías creadas; no se puede sugerir una para {}", comercio.getNombre());
            return;
        }

        String sugerida = claudeService.sugerirCategoria(comercio.getNombre(), categorias);
        Optional<Categoria> categoria = categoriaRepository.findByNombre(sugerida);
        if (categoria.isEmpty()) {
            log.warn("Claude sugirió '{}' para {}, pero esa categoría no existe", sugerida, comercio.getNombre());
            return;
        }

        transaccion.setCategoriaSugerida(categoria.get());
        transaccionRepository.save(transaccion);
        log.info("Claude sugirió '{}' para {}", sugerida, comercio.getNombre());
    }

    private List<String> nombresDeCategorias() {
        return categoriaRepository.findAllByOrderByNombreAsc().stream().map(Categoria::getNombre).toList();
    }

    private Comercio buscarOCrearComercio(String nombre) {
        return comercioRepository.findByNombre(nombre)
                .orElseGet(() -> comercioRepository.save(
                        Comercio.builder().nombre(nombre).confirmadoUsuario(false).build()));
    }

    private String extraerHeader(Message msg, String nombre) {
        return msg.getPayload().getHeaders().stream()
                .filter(h -> h.getName().equalsIgnoreCase(nombre))
                .findFirst()
                .map(MessagePartHeader::getValue)
                .orElse("");
    }

    private String extraerTextoHtml(Message msg) {
        String html = buscarHtml(msg.getPayload());
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    private String buscarHtml(MessagePart part) {
        if ("text/html".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
        }
        if (part.getParts() != null) {
            for (MessagePart sub : part.getParts()) {
                String html = buscarHtml(sub);
                if (html != null) return html;
            }
        }
        return null;
    }
}
