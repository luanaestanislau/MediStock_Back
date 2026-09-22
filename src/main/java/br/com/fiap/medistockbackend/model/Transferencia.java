package br.com.fiap.medistockbackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transferencias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_estoque_id", nullable = false)
    private ItemEstoque itemEstoque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_origem_id", nullable = false)
    private Hospital hospitalOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_destino_id", nullable = false)
    private Hospital hospitalDestino;

    @Column(nullable = false)
    private Integer quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusLogistico status = StatusLogistico.PENDENTE;

    @Column(name = "distancia_km")
    private Double distanciaKm;

    @Column(name = "tempo_estimado_min")
    private Double tempoEstimadoMinutos;

    @Column(length = 300)
    private String motivo; 

    @Column(name = "gerado_por_ia", nullable = false)
    @Builder.Default
    private boolean geradoPorIa = false;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void aoPersistir() {
        this.criadoEm = LocalDateTime.now();
    }
}
