package com.tovalh.finmail.repository;

import com.tovalh.finmail.model.Comercio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComercioRepository extends JpaRepository<Comercio, Long> {
    Optional<Comercio> findByNombre(String nombre);
}
