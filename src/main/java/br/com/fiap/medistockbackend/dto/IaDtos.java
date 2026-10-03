package br.com.fiap.medistockbackend.dto;

import java.util.List;
import jakarta.validation.constraints.Positive;

public class IaDtos {
    
    public record HistoricoConsumoRequest(
            Long itemEstoqueId,
            Long hospitalId,
            String mesReferencia, // formato "yyyy-MM-dd" ou "yyyy-MM"
            Integer quantidadeConsumida
    ) {}

    public record AnaliseInternaResponse(
            int scoreOtimizacao,      // 0-100
            String classificacao,     // OK, ATENCAO, CRITICO
            long itensCriticos,
            long itensPrioritarios,
            long previsoesGeradas,
            List<InsightItemResponse> insights
    ) {}

    public record InsightItemResponse(
            Long itemEstoqueId,
            String itemNome,
            String hospitalNome,
            int demandaProjetadaUnidades,
            int demandaProjetadaDias,
            double mediaMovelSimples,
            int sugestaoCompraUnidades,
            int confiancaPercentual
    ) {}

    public record CandidatoHospitalResponse(
            Long hospitalId,
            String hospitalNome,
            double demandaHistoricaMedia,
            double distanciaPonderadaKm,
            double pontuacao 
    ) {}

    public record RotaSugeridaResponse(
            Long hospitalOrigemId,
            String hospitalOrigemNome,
            Long hospitalDestinoId,
            String hospitalDestinoNome,
            double distanciaKm,
            double tempoEstimadoMinutos
    ) {}

    public record RedistribuicaoResponse(
            Long itemEstoqueId,
            String itemNome,
            Long hospitalAtualId,
            String hospitalAtualNome,
            Long hospitalIdealId,
            String hospitalIdealNome,
            boolean necessitaTransferencia,
            RotaSugeridaResponse rotaSugerida,
            List<CandidatoHospitalResponse> candidatos,
            String justificativaIA
    ) {}

    public record ConfirmarRedistribuicaoRequest(@Positive Integer quantidade) {}
}
