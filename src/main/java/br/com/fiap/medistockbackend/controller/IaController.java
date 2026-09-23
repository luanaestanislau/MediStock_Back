package br.com.fiap.medistockbackend.controller;

import br.com.fiap.medistockbackend.dto.IaDtos.AnaliseInternaResponse;
import br.com.fiap.medistockbackend.dto.IaDtos.RedistribuicaoResponse;
import br.com.fiap.medistockbackend.dto.IaDtos.ConfirmarRedistribuicaoRequest;
import br.com.fiap.medistockbackend.dto.LogisticaDtos.TransferenciaResponse;
import br.com.fiap.medistockbackend.service.AnaliseInternaService;
import br.com.fiap.medistockbackend.service.RedistribuicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
@Tag(name = "IA", description = "Tela 'IA': analise interna preditiva + redistribuicao de insumos de alto custo/baixa demanda")
public class IaController {

    private final AnaliseInternaService analiseInternaService;
    private final RedistribuicaoService redistribuicaoService;

    @GetMapping("/analise-interna")
    @Operation(summary = "Score de otimizacao do estoque + previsao de demanda dos itens criticos (media movel simples)")
    public AnaliseInternaResponse analiseInterna() {
        return analiseInternaService.analisar();
    }

    @GetMapping("/redistribuicao")
    @Operation(summary = "Roda a IA para TODOS os insumos de alto custo/baixa demanda em estado critico ou atencao, "
            + "retornando o hospital ideal e a rota sugerida para cada um (usado pela tela de Logistica/mapa)")
    public java.util.List<RedistribuicaoResponse> redistribuicaoTodos() {
        return redistribuicaoService.sugerirTodos();
    }

    @GetMapping("/redistribuicao/{itemEstoqueId}")
    @Operation(summary = "Calcula o melhor hospital da rede para armazenar um insumo de alto custo/baixa demanda, "
            + "e a rota mais rapida caso seja necessario transferir")
    public RedistribuicaoResponse redistribuicao(@PathVariable Long itemEstoqueId) {
        return redistribuicaoService.sugerirMelhorHospital(itemEstoqueId);
    }

    @PostMapping("/redistribuicao/{itemEstoqueId}/confirmar")
    @Operation(summary = "Confirma a sugestao da IA e cria uma transferencia pendente para o hospital recomendado")
    public ResponseEntity<TransferenciaResponse> confirmarRedistribuicao(
            @PathVariable Long itemEstoqueId,
            @Valid @RequestBody(required = false) ConfirmarRedistribuicaoRequest request) {
        Integer quantidade = request == null ? null : request.quantidade();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(redistribuicaoService.confirmarSugestao(itemEstoqueId, quantidade));
    }
}
