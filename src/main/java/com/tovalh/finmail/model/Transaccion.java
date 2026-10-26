package com.tovalh.finmail.model;

import jakarta.persistence.*;
import lombok.*;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaccion")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaccion extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "gmail_message_id", nullable = false, unique = true)
    private String gmailMessageId;
    @Column(nullable = false)
    private Integer monto;
    @Column(name = "fecha", nullable = false, columnDefinition = "timestamp(0)")
    private LocalDateTime fecha;
    @Column(name = "emisor_email", nullable = false)
    private String emisorEmail;
    @Column(name = "numero_cuenta")
    private String numeroCuenta;
    @Enumerated(STRING)
    @Column(name = "tipo_transaccion", nullable = false)
    private TipoTransaccion tipoTransaccion;
    @Column(name = "destinatario")
    private String destinatario;
    @Column(name = "mensaje")
    private String mensaje;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comercio", nullable = false)
    private Comercio comercio;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_sugerida")
    private Categoria categoriaSugerida;
    @Builder.Default
    @Column(nullable = false)
    private Boolean eliminado = false;
}
