package br.com.fiap.medistockbackend.controller;

import br.com.fiap.medistockbackend.dto.AuditoriaResponse;
import br.com.fiap.medistockbackend.model.AcaoAuditoria;
import br.com.fiap.medistockbackend.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Trilha de auditoria Smart HAS: quem alterou estoque, hospitais, logistica e acessos")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    @Operation(summary = "Lista os registros de auditoria mais recentes (filtros opcionais por usuario, acao e recurso)")
    public List<AuditoriaResponse> listar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) AcaoAuditoria acao,
            @RequestParam(required = false) String recurso,
            @RequestParam(defaultValue = "100") int limite) {
        return auditoriaService.buscar(usuario, acao, recurso, limite);
    }
}
