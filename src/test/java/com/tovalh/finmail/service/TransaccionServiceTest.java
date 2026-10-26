package com.tovalh.finmail.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.tovalh.finmail.model.Comercio;
import com.tovalh.finmail.model.TipoTransaccion;
import com.tovalh.finmail.model.Transaccion;
import com.tovalh.finmail.parser.BancoChileParser;
import com.tovalh.finmail.repository.CategoriaRepository;
import com.tovalh.finmail.repository.ComercioRepository;
import com.tovalh.finmail.repository.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceTest {

    @Mock private GmailService gmailService;
    @Mock private ClaudeService claudeService;
    @Mock private TransaccionRepository transaccionRepository;
    @Mock private ComercioRepository comercioRepository;
    @Mock private CategoriaRepository categoriaRepository;

    private TransaccionService transaccionService;

    private final Comercio comercioSinCategoria = Comercio.builder()
            .nombre("AMISTAD")
            .confirmadoUsuario(false)
            .build();

    @BeforeEach
    void setUp() {
        transaccionService = new TransaccionService(
                gmailService, claudeService, transaccionRepository, comercioRepository, categoriaRepository,
                List.of(new BancoChileParser())
        );
    }

    @Test
    void procesarCorreosNuevos_parsea_cargo_correctamente() throws Exception {
        Message msg = cargoMessage("cargo-001",
                "compra por $7.580 en AMISTAD el 13/03/2026 14:13 con Cuenta ****8704");

        when(gmailService.fetchBankMessages()).thenReturn(List.of(msg));
        when(transaccionRepository.existsByGmailMessageId("cargo-001")).thenReturn(false);
        when(comercioRepository.findByNombre("AMISTAD")).thenReturn(Optional.empty());
        when(comercioRepository.save(any())).thenReturn(comercioSinCategoria);
        when(categoriaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        when(transaccionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        transaccionService.procesarCorreosNuevos();

        Transaccion guardada = captor.getValue();
        assertThat(guardada.getMonto()).isEqualTo(7580);
        assertThat(guardada.getTipoTransaccion()).isEqualTo(TipoTransaccion.CARGO);
        assertThat(guardada.getFecha()).isEqualTo(LocalDateTime.of(2026, 3, 13, 14, 13));
        assertThat(guardada.getNumeroCuenta()).isEqualTo("****8704");
        assertThat(guardada.getGmailMessageId()).isEqualTo("cargo-001");
    }

    @Test
    void procesarCorreosNuevos_parsea_transferencia_correctamente() throws Exception {
        String body = "Monto $16.400 N&ordm; de Cuenta 00-225-63987-04 " +
                "Nombre y Apellido Catherine Arriagada Rut " +
                "Mensaje Luz + Agua Febrero 2026 Fecha martes 10 de marzo de 2026 19:21";
        Message msg = transferenciaMessage("transfer-001", body);

        when(gmailService.fetchBankMessages()).thenReturn(List.of(msg));
        when(transaccionRepository.existsByGmailMessageId("transfer-001")).thenReturn(false);
        Comercio comercioTransferencia = Comercio.builder()
                .nombre("Transferencia a Catherine Arriagada")
                .confirmadoUsuario(false)
                .build();
        when(comercioRepository.findByNombre("Transferencia a Catherine Arriagada")).thenReturn(Optional.empty());
        when(comercioRepository.save(any())).thenReturn(comercioTransferencia);
        when(categoriaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        when(transaccionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        transaccionService.procesarCorreosNuevos();

        Transaccion guardada = captor.getValue();
        assertThat(guardada.getMonto()).isEqualTo(16400);
        assertThat(guardada.getTipoTransaccion()).isEqualTo(TipoTransaccion.TRANSFERENCIA);
        assertThat(guardada.getFecha()).isEqualTo(LocalDateTime.of(2026, 3, 10, 19, 21));
        assertThat(guardada.getDestinatario()).isEqualTo("Catherine Arriagada");
        assertThat(guardada.getMensaje()).isEqualTo("Luz + Agua Febrero 2026");
        assertThat(guardada.getComercio().getNombre()).isEqualTo("Transferencia a Catherine Arriagada");
    }

    @Test
    void transferencias_a_distintos_destinatarios_usan_comercios_distintos() throws Exception {
        String aCatherine = "Monto $16.400 N&ordm; de Cuenta 00-225-63987-04 " +
                "Nombre y Apellido Catherine Arriagada Rut " +
                "Mensaje Luz Febrero Fecha martes 10 de marzo de 2026 19:21";
        String aPedro = "Monto $9.000 N&ordm; de Cuenta 00-225-63987-04 " +
                "Nombre y Apellido Pedro Soto Rut " +
                "Mensaje Almuerzo Fecha martes 10 de marzo de 2026 20:30";

        when(gmailService.fetchBankMessages()).thenReturn(List.of(
                transferenciaMessage("transfer-a", aCatherine),
                transferenciaMessage("transfer-b", aPedro)
        ));
        when(transaccionRepository.existsByGmailMessageId(anyString())).thenReturn(false);
        when(comercioRepository.findByNombre(anyString())).thenReturn(Optional.empty());
        when(comercioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transaccionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoriaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());

        transaccionService.procesarCorreosNuevos();

        ArgumentCaptor<Comercio> captor = ArgumentCaptor.forClass(Comercio.class);
        verify(comercioRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(Comercio::getNombre)
                .containsExactly("Transferencia a Catherine Arriagada", "Transferencia a Pedro Soto");
    }

    @Test
    void procesarCorreosNuevos_omite_mensaje_ya_procesado() throws Exception {
        Message msg = cargoMessage("duplicado-001",
                "compra por $1.000 en COMERCIO el 01/01/2026 10:00 con Cuenta ****1234");

        when(gmailService.fetchBankMessages()).thenReturn(List.of(msg));
        when(transaccionRepository.existsByGmailMessageId("duplicado-001")).thenReturn(true);

        transaccionService.procesarCorreosNuevos();

        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void procesarCorreosNuevos_ignora_remitentes_sin_parser() throws Exception {
        MessagePartHeader from = new MessagePartHeader().setName("From").setValue("avisos@otrobanco.cl");
        Message msg = new Message()
                .setId("otro-001")
                .setSnippet("compra por $5.000 en LIDER el 01/01/2026 10:00")
                .setPayload(new MessagePart().setHeaders(List.of(from)));

        when(gmailService.fetchBankMessages()).thenReturn(List.of(msg));
        when(transaccionRepository.existsByGmailMessageId("otro-001")).thenReturn(false);

        transaccionService.procesarCorreosNuevos();

        verify(transaccionRepository, never()).save(any());
        verifyNoInteractions(claudeService);
    }

    @Test
    void cargarHistorico_cuenta_solo_los_movimientos_guardados() throws Exception {
        Message valido = cargoMessage("hist-001",
                "compra por $3.200 en COPEC el 02/01/2026 09:15 con Cuenta ****8704");
        Message ilegible = cargoMessage("hist-002", "Actualizamos nuestros términos y condiciones");

        when(gmailService.fetchAllBankMessagesSince("2026/01/01")).thenReturn(List.of(valido, ilegible));
        when(transaccionRepository.existsByGmailMessageId(anyString())).thenReturn(false);
        when(comercioRepository.findByNombre("COPEC")).thenReturn(Optional.empty());
        when(comercioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transaccionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoriaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());

        int guardados = transaccionService.cargarHistorico("2026/01/01");

        assertThat(guardados).isEqualTo(1);
        verifyNoInteractions(claudeService);
    }

    private Message cargoMessage(String id, String snippet) {
        MessagePartHeader from = new MessagePartHeader()
                .setName("From")
                .setValue("enviodigital@bancochile.cl");
        return new Message()
                .setId(id)
                .setSnippet(snippet)
                .setPayload(new MessagePart().setHeaders(List.of(from)));
    }

    private Message transferenciaMessage(String id, String bodyText) {
        String encoded = Base64.getUrlEncoder()
                .encodeToString(bodyText.getBytes(StandardCharsets.UTF_8));

        MessagePart htmlPart = new MessagePart()
                .setMimeType("text/html")
                .setBody(new MessagePartBody().setData(encoded));

        MessagePartHeader from = new MessagePartHeader()
                .setName("From")
                .setValue("serviciodetransferencias@bancochile.cl");

        return new Message()
                .setId(id)
                .setPayload(new MessagePart()
                        .setHeaders(List.of(from))
                        .setParts(List.of(htmlPart)));
    }
}
