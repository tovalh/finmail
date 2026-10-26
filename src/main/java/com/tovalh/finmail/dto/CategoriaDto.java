package com.tovalh.finmail.dto;

import com.tovalh.finmail.model.Categoria;
import jakarta.validation.constraints.NotBlank;

public record CategoriaDto(Long id, @NotBlank String nombre, String color, String icono) {

    public static CategoriaDto from(Categoria categoria) {
        return new CategoriaDto(categoria.getId(), categoria.getNombre(), categoria.getColor(), categoria.getIcono());
    }
}
