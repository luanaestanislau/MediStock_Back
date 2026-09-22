package br.com.fiap.medistockbackend.repository;

import br.com.fiap.medistockbackend.model.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
}
