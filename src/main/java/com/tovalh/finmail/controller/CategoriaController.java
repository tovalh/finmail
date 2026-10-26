package com.tovalh.finmail.controller;

import com.tovalh.finmail.dto.CategoriaDto;
import com.tovalh.finmail.model.Categoria;
import com.tovalh.finmail.repository.CategoriaRepository;
import com.tovalh.finmail.repository.TransaccionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorías", description = "Gestión de categorías de gasto")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;

    public CategoriaController(CategoriaRepository categoriaRepository, TransaccionRepository transaccionRepository) {
        this.categoriaRepository = categoriaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    @Operation(summary = "Listar categorías")
    @GetMapping
    public List<CategoriaDto> listar() {
        return categoriaRepository.findAll()
                .stream()
                .map(CategoriaDto::from)
                .toList();
    }

    @Operation(summary = "Crear categoría", description = "Falla con 409 si ya existe una categoría con ese nombre")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaDto crear(@Valid @RequestBody CategoriaDto request) {
        String nombre = request.nombre().trim();

        if (categoriaRepository.findByNombre(nombre).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría llamada \"" + nombre + "\"");
        }

        Categoria.CategoriaBuilder builder = Categoria.builder().nombre(nombre);
        if (tieneValor(request.color())) builder.color(request.color());
        if (tieneValor(request.icono())) builder.icono(request.icono());

        return CategoriaDto.from(categoriaRepository.save(builder.build()));
    }

    private static boolean tieneValor(String s) {
        return s != null && !s.isBlank();
    }

    record CategoriaUpdateRequest(String nombre, String color, String icono) {}

    @Operation(summary = "Actualizar categoría", description = "Falla con 409 si el nombre ya lo usa otra categoría")
    @PatchMapping("/{id}")
    public CategoriaDto actualizar(@PathVariable Long id, @RequestBody CategoriaUpdateRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        if (tieneValor(request.nombre())) {
            String nombre = request.nombre().trim();
            categoriaRepository.findByNombre(nombre).ifPresent(otra -> {
                if (!otra.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría llamada \"" + nombre + "\"");
                }
            });
            categoria.setNombre(nombre);
        }
        if (tieneValor(request.color())) categoria.setColor(request.color());
        if (tieneValor(request.icono())) categoria.setIcono(request.icono());

        return CategoriaDto.from(categoriaRepository.save(categoria));
    }

    @Operation(summary = "Eliminar categoría", description = "Falla con 409 si tiene transacciones asociadas")
    @Transactional
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada");
        }
        if (transaccionRepository.existsByCategoriaIdAndEliminadoFalse(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría tiene transacciones asociadas");
        }
        categoriaRepository.deleteById(id);
    }
}
