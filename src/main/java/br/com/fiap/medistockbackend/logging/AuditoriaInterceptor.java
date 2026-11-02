package br.com.fiap.medistockbackend.logging;

import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.model.RegistroAuditoria;
import br.com.fiap.medistockbackend.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Audita automaticamente toda requisicao que altera dados (POST/PUT/PATCH/DELETE) em /api/**:
 * estoque, hospitais, transferencias, entregas, confirmacoes da IA etc.
 * Rotas de /api/auth sao auditadas no AuthService, onde o e-mail ja e conhecido.
 */
@Component
@RequiredArgsConstructor
public class AuditoriaInterceptor implements HandlerInterceptor {

    private static final Set<String> METODOS_AUDITADOS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditoriaService auditoriaService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String metodo = request.getMethod();
        String uri = request.getRequestURI();
        if (!METODOS_AUDITADOS.contains(metodo) || uri.startsWith("/api/auth")) {
            return;
        }

        List<String> recurso = new ArrayList<>();
        String recursoId = null;
        for (String segmento : uri.replaceFirst("^/api/?", "").split("/")) {
            if (segmento.isBlank()) {
                continue;
            }
            if (segmento.chars().allMatch(Character::isDigit)) {
                if (recursoId == null) {
                    recursoId = segmento;
                }
            } else {
                recurso.add(segmento);
            }
        }

        auditoriaService.registrar(RegistroAuditoria.builder()
                .usuario(usuarioAtual())
                .acao(acaoDoMetodo(metodo))
                .recurso(String.join("/", recurso))
                .recursoId(recursoId)
                .metodoHttp(metodo)
                .endpoint(uri)
                .statusHttp(response.getStatus())
                .ip(RequestLoggingFilter.ipCliente(request))
                .detalhes(ex != null ? "Erro: " + ex.getClass().getSimpleName() : null)
                .build());
    }

    private static AcaoAuditoria acaoDoMetodo(String metodo) {
        return switch (metodo) {
            case "POST" -> AcaoAuditoria.CRIAR;
            case "DELETE" -> AcaoAuditoria.EXCLUIR;
            default -> AcaoAuditoria.ATUALIZAR;
        };
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return AuditoriaService.USUARIO_ANONIMO;
        }
        return auth.getName();
    }
}
