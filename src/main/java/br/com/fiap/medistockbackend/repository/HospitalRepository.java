package br.com.fiap.medistockbackend.repository;

import br.com.fiap.medistockbackend.model.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
}
