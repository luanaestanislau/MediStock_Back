package br.com.fiap.medistockbackend.automacao;

import br.com.fiap.medistockbackend.dto.ResumoAlertasResponse;
import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.model.RegistroAuditoria;
import br.com.fiap.medistockbackend.service.AlertaService;
import br.com.fiap.medistockbackend.service.AuditoriaService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static br.com.fiap.medistockbackend.logging.RequestLoggingFilter.MDC_REQUEST_ID;

/**
 * Rotinas automaticas do Smart HAS:
 *  - varredura periodica de alertas (estoque critico, vencidos, validade proxima, logistica pendente);
 *  - expurgo da trilha de auditoria conforme a politica de retencao.
 * Desligue tudo com medistock.automacao.habilitada=false.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "medistock.automacao.habilitada", havingValue = "true", matchIfMissing = true)
public class AutomacoesAgendadas {

    private final AlertaService alertaService;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transacaoLeitura;

    @Value("${medistock.auditoria.retencao-dias:365}")
    private long retencaoDias;

    public AutomacoesAgendadas(AlertaService alertaService, AuditoriaService auditoriaService,
                               PlatformTransactionManager transactionManager) {
        this.alertaService = alertaService;
        this.auditoriaService = auditoriaService;
        // Os relacionamentos sao LAZY e o job roda fora de uma requisicao HTTP (sem open-in-view)
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
    }

    @Scheduled(cron = "${medistock.automacao.varredura-alertas.cron:0 0 * * * *}",
            zone = "${medistock.automacao.zona:America/Sao_Paulo}")
    public void varrerAlertas() {
        MDC.put(MDC_REQUEST_ID, "job-alertas");
        try {
            ResumoAlertasResponse resumo = transacaoLeitura.execute(status -> alertaService.resumo());
            String detalhes = "criticos=%d, atencao=%d, info=%d"
                    .formatted(resumo.criticos(), resumo.atencao(), resumo.info());

            if (resumo.criticos() > 0) {
                log.warn("[AUTOMACAO] Varredura de alertas: {} alerta(s) CRITICO(s) - {}", resumo.criticos(), detalhes);
            } else {
                log.info("[AUTOMACAO] Varredura de alertas concluida - {}", detalhes);
            }

            auditoriaService.registrar(RegistroAuditoria.builder()
                    .usuario(AuditoriaService.USUARIO_SISTEMA)
                    .acao(AcaoAuditoria.AUTOMACAO)
                    .recurso("alertas/varredura")
                    .detalhes(detalhes)
                    .build());
        } catch (Exception ex) {
            log.error("[AUTOMACAO] Falha na varredura de alertas: {}", ex.getMessage(), ex);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    @Scheduled(cron = "${medistock.automacao.expurgo-auditoria.cron:0 30 3 * * *}",
            zone = "${medistock.automacao.zona:America/Sao_Paulo}")
    public void expurgarAuditoriaAntiga() {
        MDC.put(MDC_REQUEST_ID, "job-expurgo");
        try {
            LocalDateTime limite = LocalDateTime.now().minusDays(retencaoDias);
            int removidos = auditoriaService.expurgarAnterioresA(limite);
            log.info("[AUTOMACAO] Expurgo de auditoria: {} registro(s) anteriores a {} removido(s)", removidos, limite);

            if (removidos > 0) {
                auditoriaService.registrar(RegistroAuditoria.builder()
                        .usuario(AuditoriaService.USUARIO_SISTEMA)
                        .acao(AcaoAuditoria.AUTOMACAO)
                        .recurso("auditoria/expurgo")
                        .detalhes("%d registro(s) com mais de %d dia(s) removido(s)".formatted(removidos, retencaoDias))
                        .build());
            }
        } catch (Exception ex) {
            log.error("[AUTOMACAO] Falha no expurgo de auditoria: {}", ex.getMessage(), ex);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
