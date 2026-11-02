package br.com.fiap.medistockbackend.repository;

import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.model.RegistroAuditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    @Query("""
            select a from RegistroAuditoria a
            where (:usuario is null or lower(a.usuario) = lower(:usuario))
              and (:acao is null or a.acao = :acao)
              and (:recurso is null or a.recurso like concat(:recurso, '%'))
            order by a.dataHora desc
            """)
    List<RegistroAuditoria> buscar(@Param("usuario") String usuario,
                                   @Param("acao") AcaoAuditoria acao,
                                   @Param("recurso") String recurso,
                                   Pageable pageable);

    @Modifying
    @Query("delete from RegistroAuditoria a where a.dataHora < :limite")
    int excluirAnterioresA(@Param("limite") LocalDateTime limite);
}
