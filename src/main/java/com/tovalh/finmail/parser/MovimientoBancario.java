package com.tovalh.finmail.parser;

import com.tovalh.finmail.model.TipoTransaccion;

import java.time.LocalDateTime;

/**
 * Movimiento leído de un correo, todavía sin guardar.
 * {@code comercio} es la clave con la que se aprende la categoría, así que cada parser
 * debe nombrarlo de forma estable (por ejemplo, una transferencia por destinatario).
 */
public record MovimientoBancario(
        TipoTransaccion tipo,
        int monto,
        LocalDateTime fecha,
        String comercio,
        String numeroCuenta,
        String destinatario,
        String mensaje
) {
}
