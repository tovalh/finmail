package com.tovalh.finmail.parser;

import com.tovalh.finmail.model.TipoTransaccion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Banco de Chile manda dos tipos de aviso:
 * compras con tarjeta (los datos vienen en el snippet) y transferencias (vienen en el HTML).
 */
@Component
public class BancoChileParser implements ParserCorreoBanco {

    static final String REMITENTE_CARGO = "enviodigital@bancochile.cl";
    static final String REMITENTE_TRANSFERENCIA = "serviciodetransferencias@bancochile.cl";

    // Compra: "compra por $7.580 en AMISTAD el 13/03/2026 14:13 con Cuenta ****8704"
    private static final Pattern CARGO_MONTO = Pattern.compile("por \\$(\\d[\\d.]+)");
    private static final Pattern CARGO_COMERCIO = Pattern.compile("en ([A-Za-záéíóúÁÉÍÓÚñÑ0-9.*/ ]+) el \\d{2}/\\d{2}/\\d{4}");
    private static final Pattern CARGO_FECHA = Pattern.compile("el (\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})");
    private static final Pattern CARGO_CUENTA = Pattern.compile("Cuenta (\\*+\\d+)");
    private static final DateTimeFormatter FORMATO_CARGO = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    // Transferencia: "Monto $16.400 N&ordm; de Cuenta 00-225-63987-04 Nombre y Apellido Catherine Arriagada Rut
    //                 ... Mensaje Luz + Agua Fecha martes 10 de marzo de 2026 19:21"
    private static final Pattern TRANSF_MONTO = Pattern.compile("Monto \\$(\\d[\\d.]+)");
    private static final Pattern TRANSF_CUENTA_ORIGEN = Pattern.compile("N&ordm; de Cuenta ([\\d-]+)");
    private static final Pattern TRANSF_DESTINATARIO = Pattern.compile("Nombre y Apellido ([A-Za-záéíóúÁÉÍÓÚñÑ ]+) Rut");
    private static final Pattern TRANSF_MENSAJE = Pattern.compile("Mensaje (.+?) Fecha");
    private static final Pattern TRANSF_FECHA = Pattern.compile("(\\d{1,2}) de (\\p{L}+) de (\\d{4}) (\\d{2}:\\d{2})");
    // Los meses vienen escritos en español: el locale es obligatorio.
    private static final DateTimeFormatter FORMATO_TRANSF =
            DateTimeFormatter.ofPattern("d MMMM uuuu HH:mm", Locale.of("es", "CL"))
                    .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public List<String> remitentes() {
        return List.of(REMITENTE_CARGO, REMITENTE_TRANSFERENCIA);
    }

    @Override
    public Optional<MovimientoBancario> parsear(CorreoBanco correo) {
        if (correo.remitente().contains(REMITENTE_CARGO)) return parsearCargo(correo.snippet());
        if (correo.remitente().contains(REMITENTE_TRANSFERENCIA)) return parsearTransferencia(correo.cuerpo());
        return Optional.empty();
    }

    Optional<MovimientoBancario> parsearCargo(String texto) {
        if (texto == null) return Optional.empty();

        Integer monto = buscar(CARGO_MONTO, texto).map(BancoChileParser::montoSinPuntos).orElse(null);
        String comercio = buscar(CARGO_COMERCIO, texto).map(String::trim).orElse(null);
        LocalDateTime fecha = buscar(CARGO_FECHA, texto).map(f -> fecha(f, FORMATO_CARGO)).orElse(null);
        if (monto == null || comercio == null || fecha == null) return Optional.empty();

        return Optional.of(new MovimientoBancario(
                TipoTransaccion.CARGO, monto, fecha, comercio,
                buscar(CARGO_CUENTA, texto).orElse(null), null, null));
    }

    Optional<MovimientoBancario> parsearTransferencia(String texto) {
        if (texto == null) return Optional.empty();

        Integer monto = buscar(TRANSF_MONTO, texto).map(BancoChileParser::montoSinPuntos).orElse(null);
        LocalDateTime fecha = fechaTransferencia(texto);
        if (monto == null || fecha == null) return Optional.empty();

        String destinatario = buscar(TRANSF_DESTINATARIO, texto).map(String::trim).orElse(null);
        // Un comercio por destinatario, para que la categoría aprendida no se mezcle entre personas
        String comercio = destinatario != null ? "Transferencia a " + destinatario : "Transferencia Banco de Chile";

        return Optional.of(new MovimientoBancario(
                TipoTransaccion.TRANSFERENCIA, monto, fecha, comercio,
                buscar(TRANSF_CUENTA_ORIGEN, texto).orElse(null),
                destinatario,
                buscar(TRANSF_MENSAJE, texto).map(String::trim).orElse(null)));
    }

    private static LocalDateTime fechaTransferencia(String texto) {
        Matcher m = TRANSF_FECHA.matcher(texto);
        if (!m.find()) return null;
        return fecha(m.group(1) + " " + m.group(2) + " " + m.group(3) + " " + m.group(4), FORMATO_TRANSF);
    }

    private static Optional<String> buscar(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static int montoSinPuntos(String monto) {
        return Integer.parseInt(monto.replace(".", ""));
    }

    private static LocalDateTime fecha(String texto, DateTimeFormatter formato) {
        try {
            return LocalDateTime.parse(texto, formato);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
