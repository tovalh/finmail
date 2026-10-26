package com.tovalh.finmail.dto;

import com.tovalh.finmail.model.TipoTransaccion;
import com.tovalh.finmail.model.Transaccion;

import java.time.LocalDateTime;

public record TransaccionDto(
        Long id,
        Integer monto,
        LocalDateTime fecha,
        TipoTransaccion tipoTransaccion,
        String comercioNombre,
        String categoriaNombre,
        String categoriaSugeridaNombre,
        String mensaje,
        String destinatario
) {
    public static TransaccionDto from(Transaccion transaccion) {
        return new TransaccionDto(
                transaccion.getId(),
                transaccion.getMonto(),
                transaccion.getFecha(),
                transaccion.getTipoTransaccion(),
                transaccion.getComercio() != null ? transaccion.getComercio().getNombre() : null,
                transaccion.getCategoria() != null ? transaccion.getCategoria().getNombre() : null,
                transaccion.getCategoriaSugerida() != null ? transaccion.getCategoriaSugerida().getNombre() : null,
                transaccion.getMensaje(),
                transaccion.getDestinatario()
        );
    }
}
