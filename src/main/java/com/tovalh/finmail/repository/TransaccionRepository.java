package com.tovalh.finmail.repository;

import com.tovalh.finmail.model.Transaccion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {
    boolean existsByGmailMessageId(String gmailMessageId);
    boolean existsByCategoriaIdAndEliminadoFalse(Long categoriaId);
    List<Transaccion> findByComercioIdAndCategoriaSugeridaIsNullAndEliminadoFalse(Long comercioId);

    @EntityGraph(attributePaths = {"comercio", "categoria", "categoriaSugerida"})
    List<Transaccion> findByFechaBetweenAndEliminadoFalseOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
}
