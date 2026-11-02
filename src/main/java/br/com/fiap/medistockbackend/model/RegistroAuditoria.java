package br.com.fiap.medistockbackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trilha de auditoria (Smart HAS): quem fez o que, quando e de onde.
 * Garante rastreabilidade das movimentacoes de insumos hospitalares.
 */
@Entity
@Table(name = "auditoria", indexes = {
        @Index(name = "idx_auditoria_data_hora", columnList = "data_hora"),
        @Index(name = "idx_auditoria_usuario", columnList = "usuario")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, length = 150)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcaoAuditoria acao;

    @Column(length = 120)
    private String recurso;

    @Column(name = "recurso_id", length = 40)
    private String recursoId;

    @Column(name = "metodo_http", length = 10)
    private String metodoHttp;

    @Column(length = 255)
    private String endpoint;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(length = 64)
    private String ip;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(length = 500)
    private String detalhes;

    @PrePersist
    public void aoPersistir() {
        if (this.dataHora == null) {
            this.dataHora = LocalDateTime.now();
        }
    }
}
