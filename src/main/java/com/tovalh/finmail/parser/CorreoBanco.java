package com.tovalh.finmail.parser;

/**
 * Lo que un parser necesita de un correo. {@code cuerpo} es el HTML ya convertido a texto plano.
 */
public record CorreoBanco(String remitente, String snippet, String cuerpo) {
}
