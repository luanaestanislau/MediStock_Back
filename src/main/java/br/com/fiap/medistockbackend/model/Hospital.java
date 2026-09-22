package br.com.fiap.medistockbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hospitais")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 200)
    private String endereco;

    @Column(length = 80)
    private String cidade;

    @Column(length = 2)
    private String estado;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;
}
