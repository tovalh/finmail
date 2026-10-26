package com.tovalh.finmail.controller;

import com.tovalh.finmail.model.Configuracion;
import com.tovalh.finmail.repository.ConfiguracionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config")
@Tag(name = "Configuración", description = "Parámetros de la aplicación")
@SecurityRequirement(name = "bearerAuth")
public class ConfiguracionController {

    private final ConfiguracionRepository configuracionRepository;

    public ConfiguracionController(ConfiguracionRepository configuracionRepository) {
        this.configuracionRepository = configuracionRepository;
    }

    record ConfiguracionDto(@NotNull @Min(1) @Max(31) Integer diaInicioMes) {}

    @Operation(summary = "Obtener configuración actual")
    @GetMapping
    public ConfiguracionDto obtener() {
        Configuracion config = configuracionRepository.findById(1L)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));
        return new ConfiguracionDto(config.getDiaInicioMes());
    }

    @Operation(summary = "Actualizar día de inicio del mes contable", description = "Acepta valores entre 1 y 31")
    @PatchMapping
    public ConfiguracionDto actualizar(@Valid @RequestBody ConfiguracionDto request) {
        Configuracion config = configuracionRepository.findById(1L)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));
        config.setDiaInicioMes(request.diaInicioMes());
        return new ConfiguracionDto(configuracionRepository.save(config).getDiaInicioMes());
    }
}
