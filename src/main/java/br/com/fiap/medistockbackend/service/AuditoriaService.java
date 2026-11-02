package br.com.fiap.medistockbackend.service;

import br.com.fiap.medistockbackend.dto.AuditoriaResponse;
import br.com.fiap.medistockbackend.logging.RequestLoggingFilter;
import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.model.RegistroAuditoria;
import br.com.fiap.medistockbackend.repository.RegistroAuditoriaRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AuditoriaService {

    public static final String USUARIO_SISTEMA = "sistema";
    public static final String USUARIO_ANONIMO = "anonimo";
    private static final int LIMITE_MAXIMO_CONSULTA = 500;

    private final RegistroAuditoriaRepository repository;
    private final TransactionTemplate transacaoIsolada;

    public AuditoriaService(RegistroAuditoriaRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transacaoIsolada = new TransactionTemplate(transactionManager);
        this.transacaoIsolada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Grava o registro em transacao propria: uma falha na auditoria nunca derruba
     * a operacao de negocio. Se ja existir uma transacao aberta, a gravacao e adiada
     * para depois dela terminar (commit ou rollback) - o SQLite so aceita um escritor
     * por vez e travaria se as duas transacoes escrevessem ao mesmo tempo.
     */
    public void registrar(RegistroAuditoria registro) {
        if (registro.getRequestId() == null) {
            registro.setRequestId(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID));
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        registro.setDetalhes(registro.getDetalhes() == null
                                ? "Transacao revertida"
                                : "Transacao revertida - " + registro.getDetalhes());
                    }
                    gravar(registro);
                }
            });
        } else {
            gravar(registro);
        }
    }

    private void gravar(RegistroAuditoria registro) {
        try {
            transacaoIsolada.executeWithoutResult(status -> repository.save(registro));
            log.info("[AUDITORIA] usuario={} acao={} recurso={} recursoId={} status={}",
                    registro.getUsuario(), registro.getAcao(), registro.getRecurso(),
                    registro.getRecursoId(), registro.getStatusHttp());
        } catch (Exception ex) {
            log.error("Falha ao gravar registro de auditoria: {}", ex.getMessage(), ex);
        }
    }

    /** Atalho para eventos de autenticacao, que acontecem antes de existir usuario logado. */
    public void registrarEventoAuth(AcaoAuditoria acao, String email, String detalhes) {
        HttpServletRequest request = requestAtual();
        registrar(RegistroAuditoria.builder()
                .usuario(email == null || email.isBlank() ? USUARIO_ANONIMO : email.trim().toLowerCase())
                .acao(acao)
                .recurso("auth")
                .metodoHttp(request != null ? request.getMethod() : null)
                .endpoint(request != null ? request.getRequestURI() : null)
                .ip(request != null ? RequestLoggingFilter.ipCliente(request) : null)
                .detalhes(detalhes)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditoriaResponse> buscar(String usuario, AcaoAuditoria acao, String recurso, int limite) {
        int tamanho = Math.clamp(limite, 1, LIMITE_MAXIMO_CONSULTA);
        return repository.buscar(vazioParaNulo(usuario), acao, vazioParaNulo(recurso), PageRequest.of(0, tamanho))
                .stream()
                .map(AuditoriaResponse::fromEntity)
                .toList();
    }

    @Transactional
    public int expurgarAnterioresA(LocalDateTime limite) {
        return repository.excluirAnterioresA(limite);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static HttpServletRequest requestAtual() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
