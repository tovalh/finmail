package com.tovalh.finmail.parser;

import java.util.List;
import java.util.Optional;

/**
 * Lee los avisos de movimiento que manda un banco por correo.
 * Para sumar un banco basta con implementar esta interfaz como {@code @Component}:
 * la búsqueda en Gmail, el guardado y la categorización con Claude no cambian.
 */
public interface ParserCorreoBanco {

    /** Direcciones desde las que el banco envía los avisos. Se usan para filtrar Gmail. */
    List<String> remitentes();

    /** El movimiento del correo, o vacío si el correo no trae el formato esperado. */
    Optional<MovimientoBancario> parsear(CorreoBanco correo);

    default boolean acepta(String remitente) {
        return remitentes().stream().anyMatch(remitente::contains);
    }
}
