package com.tovalh.finmail.dto;

import java.util.List;

public record ResumenDto(
        Integer totalCargos,
        Integer totalTransferencias,
        Integer total,
        long sinCategorizar,
        List<GastoPorCategoriaDto> gastosPorCategoria
) {
    public record GastoPorCategoriaDto(String categoria, Integer monto) {}
}