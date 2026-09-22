package br.com.fiap.medistockbackend.service;

import br.com.fiap.medistockbackend.dto.IaDtos.CandidatoHospitalResponse;
import br.com.fiap.medistockbackend.dto.IaDtos.RedistribuicaoResponse;
import br.com.fiap.medistockbackend.dto.IaDtos.RotaSugeridaResponse;
import br.com.fiap.medistockbackend.exception.ResourceNotFoundException;
import br.com.fiap.medistockbackend.model.Hospital;
import br.com.fiap.medistockbackend.model.HistoricoConsumo;
import br.com.fiap.medistockbackend.model.ItemEstoque;
import br.com.fiap.medistockbackend.repository.HistoricoConsumoRepository;
import br.com.fiap.medistockbackend.repository.HospitalRepository;
import br.com.fiap.medistockbackend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedistribuicaoService {

    private final HospitalRepository hospitalRepository;
    private final HistoricoConsumoRepository historicoConsumoRepository;
    private final ItemEstoqueService itemEstoqueService;
    private final GeminiClient geminiClient;

    public RedistribuicaoResponse sugerirMelhorHospital(Long itemEstoqueId) {
        ItemEstoque item = itemEstoqueService.buscarEntidade(itemEstoqueId);
        List<Hospital> hospitais = hospitalRepository.findAll();

        if (hospitais.size() < 2) {
            throw new ResourceNotFoundException(
                    "E necessario cadastrar pelo menos 2 hospitais na rede para calcular redistribuicao");
        }

        Map<Long, Double> demandaMediaPorHospital = calcularDemandaMediaPorHospital(item, hospitais);

        List<CandidatoHospitalResponse> candidatos = hospitais.stream()
                .map(candidato -> avaliarCandidato(candidato, hospitais, demandaMediaPorHospital))
                .sorted(Comparator.comparingDouble(CandidatoHospitalResponse::pontuacao))
                .toList();

        CandidatoHospitalResponse melhor = candidatos.get(0);
        Hospital hospitalIdeal = hospitalRepository.findById(melhor.hospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital nao encontrado"));

        boolean precisaTransferir = !hospitalIdeal.getId().equals(item.getHospital().getId());

        RotaSugeridaResponse rota = null;
        if (precisaTransferir) {
            double distanciaKm = GeoUtils.distanciaKm(
                    item.getHospital().getLatitude(), item.getHospital().getLongitude(),
                    hospitalIdeal.getLatitude(), hospitalIdeal.getLongitude());
            rota = new RotaSugeridaResponse(
                    item.getHospital().getId(), item.getHospital().getNome(),
                    hospitalIdeal.getId(), hospitalIdeal.getNome(),
                    arredondar(distanciaKm), arredondar(GeoUtils.tempoEstimadoMinutos(distanciaKm))
            );
        }

        String justificativa = gerarJustificativa(item, hospitalIdeal, precisaTransferir, rota, demandaMediaPorHospital);

        return new RedistribuicaoResponse(
                item.getId(), item.getNome(),
                item.getHospital().getId(), item.getHospital().getNome(),
                hospitalIdeal.getId(), hospitalIdeal.getNome(),
                precisaTransferir, rota, candidatos, justificativa
        );
    }

    private Map<Long, Double> calcularDemandaMediaPorHospital(ItemEstoque item, List<Hospital> hospitais) {
        List<HistoricoConsumo> historico = historicoConsumoRepository
                .findByItemEstoqueIdOrderByMesReferenciaDesc(item.getId());

        return hospitais.stream().collect(Collectors.toMap(
                Hospital::getId,
                h -> historico.stream()
                        .filter(hc -> hc.getHospital().getId().equals(h.getId()))
                        .mapToInt(HistoricoConsumo::getQuantidadeConsumida)
                        .average()
                        .orElse(0.0)
        ));
    }

    private CandidatoHospitalResponse avaliarCandidato(Hospital candidato, List<Hospital> todos,
                                                         Map<Long, Double> demandaMediaPorHospital) {
        double demandaDoCandidato = demandaMediaPorHospital.getOrDefault(candidato.getId(), 0.0);

        double pontuacao = todos.stream()
                .filter(h -> !h.getId().equals(candidato.getId()))
                .mapToDouble(h -> {
                    double demanda = demandaMediaPorHospital.getOrDefault(h.getId(), 0.0);
                    double distancia = GeoUtils.distanciaKm(
                            candidato.getLatitude(), candidato.getLongitude(), h.getLatitude(), h.getLongitude());
                    return distancia * demanda;
                })
                .sum();

        return new CandidatoHospitalResponse(
                candidato.getId(), candidato.getNome(),
                arredondar(demandaDoCandidato), arredondar(pontuacao), arredondar(pontuacao)
        );
    }

    private String gerarJustificativa(ItemEstoque item, Hospital ideal, boolean precisaTransferir,
                                       RotaSugeridaResponse rota, Map<Long, Double> demandaMediaPorHospital) {

        String justificativaFallback = montarJustificativaFallback(item, ideal, precisaTransferir, rota, demandaMediaPorHospital);

        if (!geminiClient.configurado()) {
            return justificativaFallback;
        }

        String prompt = """
                Voce é um assistente de logistica hospitalar do sistema MediStock.
                Explique em até 3 frases, em português do Brasil, de forma direta e
                profissional, por que o insumo '%s' (alto custo, baixa demanda)
                deveria ser armazenado no hospital '%s' considerando o historico de
                demanda da rede. %s
                Nao invente numeros; use apenas o racional de centralidade em relacao
                a demanda historica.
                """.formatted(
                item.getNome(),
                ideal.getNome(),
                precisaTransferir && rota != null
                        ? "Mencione tambem que sera necessaria uma transferencia de %s para %s, de aproximadamente %.1f km."
                                .formatted(item.getHospital().getNome(), ideal.getNome(), rota.distanciaKm())
                        : "O item ja esta no hospital ideal, nao ha necessidade de transferencia."
        );

        String textoGerado = geminiClient.gerarTexto(prompt);
        return textoGerado != null && !textoGerado.isBlank() ? textoGerado : justificativaFallback;
    }

    private String montarJustificativaFallback(ItemEstoque item, Hospital ideal, boolean precisaTransferir,
                                                 RotaSugeridaResponse rota, Map<Long, Double> demandaMediaPorHospital) {
        String baseTexto = "Com base no historico de demanda da rede, o hospital %s e o local mais central "
                .formatted(ideal.getNome())
                + "para armazenar '%s', minimizando a distancia ponderada ate os hospitais que mais consomem esse insumo."
                        .formatted(item.getNome());

        if (precisaTransferir && rota != null) {
            baseTexto += String.format(Locale.forLanguageTag("pt-BR"),
                    " Recomenda-se transferir o item de %s para %s (~%.1f km, tempo estimado de %.0f min).",
                    item.getHospital().getNome(), ideal.getNome(), rota.distanciaKm(), rota.tempoEstimadoMinutos());
        } else {
            baseTexto += " O item ja esta armazenado no local ideal, nenhuma transferencia e necessaria no momento.";
        }
        return baseTexto;
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
