package com.tovalh.finmail.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="categoria")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Categoria extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="nombre",nullable = false, unique = true)
    private String nombre;
    @Column(name="color", nullable = false)
    @Builder.Default
    private String color = "#3B82F6";

    @Column(name="icono", nullable = false)
    @Builder.Default
    private String icono = "Tag";
}
