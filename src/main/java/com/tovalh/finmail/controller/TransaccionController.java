package com.tovalh.finmail.controller;

import com.tovalh.finmail.dto.ResumenDto;
import com.tovalh.finmail.dto.TransaccionDto;
import com.tovalh.finmail.model.Categoria;
import com.tovalh.finmail.model.TipoTransaccion;
import com.tovalh.finmail.model.Transaccion;
import com.tovalh.finmail.model.Comercio;
import com.tovalh.finmail.repository.CategoriaRepository;
import com.tovalh.finmail.repository.ComercioRepository;
import com.tovalh.finmail.repository.TransaccionRepository;
import com.tovalh.finmail.service.TransaccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transacciones")
@Tag(name = "Transacciones", description = "Listado, resumen y gestión de transacciones bancarias")
@SecurityRequirement(name = "bearerAuth")
public class TransaccionController {

    private final TransaccionRepository transaccionRepository;
    private final CategoriaRepository categoriaRepository;
    private final ComercioRepository comercioRepository;
    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionRepository transaccionRepository,
                                 CategoriaRepository categoriaRepository,
                                 ComercioRepository comercioRepository,
                                 TransaccionService transaccionService) {
        this.transaccionRepository = transaccionRepository;
        this.categoriaRepository = categoriaRepository;
        this.comercioRepository = comercioRepository;
        this.transaccionService = transaccionService;
    }

    @Operation(summary = "Listar transacciones", description = "Retorna todas las transacciones no eliminadas en el rango de fechas")
    @GetMapping
    public List<TransaccionDto> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return transaccionRepository
                .findByFechaBetweenAndEliminadoFalseOrderByFechaDesc(desde.atStartOfDay(), hasta.atTime(23, 59, 59))
                .stream()
                .map(TransaccionDto::from)
                .toList();
    }

    @Operation(summary = "Resumen del período", description = "Totales por tipo, cantidad sin categorizar y desglose por categoría")
    @GetMapping("/resumen")
    public ResumenDto resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        List<Transaccion> transacciones = transaccionRepository
                .findByFechaBetweenAndEliminadoFalseOrderByFechaDesc(desde.atStartOfDay(), hasta.atTime(23, 59, 59));

        int totalCargos = transacciones.stream()
                .filter(t -> t.getTipoTransaccion() == TipoTransaccion.CARGO)
                .mapToInt(Transaccion::getMonto)
                .sum();

        int totalTransferencias = transacciones.stream()
                .filter(t -> t.getTipoTransaccion() == TipoTransaccion.TRANSFERENCIA)
                .mapToInt(Transaccion::getMonto)
                .sum();

        long sinCategorizar = transacciones.stream()
                .filter(t -> t.getCategoria() == null)
                .count();

        Map<String, Integer> porCategoria = transacciones.stream()
                .filter(t -> t.getCategoria() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCategoria().getNombre(),
                        Collectors.summingInt(Transaccion::getMonto)
                ));

        List<ResumenDto.GastoPorCategoriaDto> gastosPorCategoria = porCategoria.entrySet().stream()
                .map(e -> new ResumenDto.GastoPorCategoriaDto(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.monto().compareTo(a.monto()))
                .toList();

        return new ResumenDto(totalCargos, totalTransferencias, totalCargos + totalTransferencias, sinCategorizar, gastosPorCategoria);
    }

    record TransaccionManualRequest(
            @NotNull @Min(1) Integer monto,
            @NotNull LocalDateTime fecha,
            @NotNull Long categoriaId,
            String comercio
    ) {}
    record HistoricoResponse(int importadas) {}
    record CategoriaRequest(@NotNull Long categoriaId) {}

    @Operation(summary = "Crear transacción manual")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionDto crearManual(@Valid @RequestBody TransaccionManualRequest request) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        String nombreComercio = (request.comercio() != null && !request.comercio().isBlank())
                ? request.comercio() : "Manual";

        Comercio comercio = comercioRepository.findByNombre(nombreComercio)
                .orElseGet(() -> comercioRepository.save(
                        Comercio.builder().nombre(nombreComercio).confirmadoUsuario(false).build()
                ));

        Transaccion transaccion = transaccionRepository.save(Transaccion.builder()
                .gmailMessageId("manual-" + UUID.randomUUID())
                .monto(request.monto())
                .fecha(request.fecha())
                .tipoTransaccion(TipoTransaccion.CARGO)
                .emisorEmail("manual")
                .comercio(comercio)
                .categoria(categoria)
                .build());

        return TransaccionDto.from(transaccion);
    }

    @Operation(summary = "Importar historial desde Gmail", description = "Carga todos los correos bancarios desde la fecha indicada")
    @PostMapping("/historico")
    public HistoricoResponse cargarHistorico(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde
    ) {
        String afterDate = desde.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        int importadas = transaccionService.cargarHistorico(afterDate);
        return new HistoricoResponse(importadas);
    }

    @Operation(summary = "Eliminar transacción (soft delete)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        Transaccion transaccion = transaccionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));
        transaccion.setEliminado(true);
        transaccionRepository.save(transaccion);
    }

    @Operation(summary = "Asignar categoría", description = "Acepta o cambia la categoría de una transacción. Actualiza también el caché del comercio.")
    @Transactional
    @PatchMapping("/{id}/categoria")
    public TransaccionDto asignarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        Transaccion transaccion = transaccionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        transaccion.setCategoria(categoria);
        transaccion.getComercio().setCategoria(categoria);

        return TransaccionDto.from(transaccionRepository.save(transaccion));
    }
}
