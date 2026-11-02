package br.com.fiap.medistockbackend.dto;

import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.model.RegistroAuditoria;

import java.time.LocalDateTime;

public record AuditoriaResponse(
        Long id,
        LocalDateTime dataHora,
        String usuario,
        AcaoAuditoria acao,
        String recurso,
        String recursoId,
        String metodoHttp,
        String endpoint,
        Integer statusHttp,
        String ip,
        String requestId,
        String detalhes
) {
    public static AuditoriaResponse fromEntity(RegistroAuditoria r) {
        return new AuditoriaResponse(r.getId(), r.getDataHora(), r.getUsuario(), r.getAcao(),
                r.getRecurso(), r.getRecursoId(), r.getMetodoHttp(), r.getEndpoint(),
                r.getStatusHttp(), r.getIp(), r.getRequestId(), r.getDetalhes());
    }
}
