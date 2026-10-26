package com.tovalh.finmail.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "configuracion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Configuracion {

    @Id
    private Long id;

    @Column(name = "dia_inicio_mes", nullable = false)
    private Integer diaInicioMes;
}