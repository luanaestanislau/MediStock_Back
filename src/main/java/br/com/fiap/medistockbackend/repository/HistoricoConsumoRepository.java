package br.com.fiap.medistockbackend.repository;

import br.com.fiap.medistockbackend.model.HistoricoConsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoConsumoRepository extends JpaRepository<HistoricoConsumo, Long> {

    List<HistoricoConsumo> findByItemEstoqueIdOrderByMesReferenciaDesc(Long itemEstoqueId);

    List<HistoricoConsumo> findByItemEstoqueIdAndHospitalIdOrderByMesReferenciaDesc(Long itemEstoqueId, Long hospitalId);
}
