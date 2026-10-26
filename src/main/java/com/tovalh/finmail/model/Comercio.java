package com.tovalh.finmail.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="comercio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Comercio extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="nombre",nullable = false)
    private String nombre;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="id_categoria")
    private Categoria categoria;
    @Column(name="confirmado_usuario",nullable = false)
    private Boolean confirmadoUsuario;
}
