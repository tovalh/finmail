package com.tovalh.finmail.parser;

import com.tovalh.finmail.model.TipoTransaccion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BancoChileParserTest {

    private final BancoChileParser parser = new BancoChileParser();

    @Test
    void parseaUnaCompraDesdeElSnippet() {
        var correo = new CorreoBanco(BancoChileParser.REMITENTE_CARGO,
                "compra por $7.580 en AMISTAD el 13/03/2026 14:13 con Cuenta ****8704", "");

        var movimiento = parser.parsear(correo).orElseThrow();

        assertThat(movimiento.tipo()).isEqualTo(TipoTransaccion.CARGO);
        assertThat(movimiento.monto()).isEqualTo(7580);
        assertThat(movimiento.comercio()).isEqualTo("AMISTAD");
        assertThat(movimiento.fecha()).isEqualTo(LocalDateTime.of(2026, 3, 13, 14, 13));
        assertThat(movimiento.numeroCuenta()).isEqualTo("****8704");
    }

    @Test
    void parseaUnaTransferenciaDesdeElCuerpo() {
        var cuerpo = "Monto $16.400 N&ordm; de Cuenta 00-225-63987-04 "
                + "Nombre y Apellido Catherine Arriagada Rut "
                + "Mensaje Luz + Agua Febrero 2026 Fecha martes 10 de marzo de 2026 19:21";
        var correo = new CorreoBanco(BancoChileParser.REMITENTE_TRANSFERENCIA, "", cuerpo);

        var movimiento = parser.parsear(correo).orElseThrow();

        assertThat(movimiento.tipo()).isEqualTo(TipoTransaccion.TRANSFERENCIA);
        assertThat(movimiento.monto()).isEqualTo(16400);
        assertThat(movimiento.fecha()).isEqualTo(LocalDateTime.of(2026, 3, 10, 19, 21));
        assertThat(movimiento.destinatario()).isEqualTo("Catherine Arriagada");
        assertThat(movimiento.mensaje()).isEqualTo("Luz + Agua Febrero 2026");
        assertThat(movimiento.numeroCuenta()).isEqualTo("00-225-63987-04");
        assertThat(movimiento.comercio()).isEqualTo("Transferencia a Catherine Arriagada");
    }

    @Test
    void unaTransferenciaSinDestinatarioUsaUnComercioGenerico() {
        var cuerpo = "Monto $9.000 Fecha martes 10 de marzo de 2026 20:30";
        var correo = new CorreoBanco(BancoChileParser.REMITENTE_TRANSFERENCIA, "", cuerpo);

        assertThat(parser.parsear(correo).orElseThrow().comercio()).isEqualTo("Transferencia Banco de Chile");
    }

    @Test
    void unCorreoConOtroFormatoNoProduceMovimiento() {
        var correo = new CorreoBanco(BancoChileParser.REMITENTE_CARGO, "Actualizamos nuestros términos y condiciones", "");

        assertThat(parser.parsear(correo)).isEmpty();
    }

    @Test
    void unaFechaInvalidaNoRompeElParseo() {
        var correo = new CorreoBanco(BancoChileParser.REMITENTE_CARGO,
                "compra por $1.000 en COMERCIO el 31/02/2026 10:00 con Cuenta ****1234", "");

        assertThat(parser.parsear(correo)).isEmpty();
    }

    @Test
    void reconoceSoloSusRemitentes() {
        assertThat(parser.acepta("Banco de Chile <enviodigital@bancochile.cl>")).isTrue();
        assertThat(parser.acepta("avisos@otrobanco.cl")).isFalse();
    }
}
