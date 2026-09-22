package br.com.fiap.medistockbackend.controller;

import br.com.fiap.medistockbackend.dto.IaDtos.AnaliseInternaResponse;
import br.com.fiap.medistockbackend.dto.IaDtos.RedistribuicaoResponse;
import br.com.fiap.medistockbackend.service.AnaliseInternaService;
import br.com.fiap.medistockbackend.service.RedistribuicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/redistribuicao/{itemEstoqueId}")
    @Operation(summary = "Calcula o melhor hospital da rede para armazenar um insumo de alto custo/baixa demanda, "
            + "e a rota mais rapida caso seja necessario transferir")
    public RedistribuicaoResponse redistribuicao(@PathVariable Long itemEstoqueId) {
        return redistribuicaoService.sugerirMelhorHospital(itemEstoqueId);
    }
}


