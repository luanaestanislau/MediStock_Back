package br.com.fiap.medistockbackend.service;

import br.com.fiap.medistockbackend.dto.AlertaResponse;
import br.com.fiap.medistockbackend.dto.ResumoAlertasResponse;
import br.com.fiap.medistockbackend.model.AlertaTipo;
import br.com.fiap.medistockbackend.model.ItemEstoque;
import br.com.fiap.medistockbackend.model.NivelEstoque;
import br.com.fiap.medistockbackend.repository.ItemEstoqueRepository;
import br.com.fiap.medistockbackend.repository.TransferenciaRepository;
import br.com.fiap.medistockbackend.model.StatusLogistico;
import br.com.fiap.medistockbackend.model.Transferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertaService {

    private static final long DIAS_LIMITE_ATENCAO_VALIDADE = 15;

    private final ItemEstoqueRepository itemEstoqueRepository;
    private final TransferenciaRepository transferenciaRepository;

    public List<AlertaResponse> listar(AlertaTipo filtro) {
        List<AlertaResponse> alertas = new ArrayList<>();

        for (ItemEstoque item : itemEstoqueRepository.findAll()) {
            alertas.addAll(gerarAlertasDoItem(item));
        }
        alertas.addAll(gerarAlertasLogisticos());

        if (filtro != null) {
            return alertas.stream().filter(a -> a.tipo() == filtro).toList();
        }
        return alertas;
    }

    private List<AlertaResponse> gerarAlertasLogisticos() {
        return transferenciaRepository.findAll().stream()
                .filter(transferencia -> transferencia.getStatus() == StatusLogistico.PENDENTE
                        || transferencia.getStatus() == StatusLogistico.EM_ROTA)
                .map(this::alertaDaTransferencia)
                .toList();
    }

    private AlertaResponse alertaDaTransferencia(Transferencia transferencia) {
        String status = transferencia.getStatus() == StatusLogistico.EM_ROTA ? "em rota" : "pendente";
        String origem = transferencia.getHospitalOrigem().getNome();
        String destino = transferencia.getHospitalDestino().getNome();
        String prefixo = transferencia.isGeradoPorIa() ? "Transferência sugerida pela IA" : "Transferência";

        return new AlertaResponse(
                transferencia.getItemEstoque().getId(),
                transferencia.getItemEstoque().getNome(),
                AlertaTipo.INFO,
                "%s %s: %s para %s (%d unidade(s), %s).".formatted(
                        prefixo, status, origem, destino, transferencia.getQuantidade(), status),
                destino,
                "Logística"
        );
    }

    public ResumoAlertasResponse resumo() {
        List<AlertaResponse> todos = listar(null);
        return new ResumoAlertasResponse(
                todos.stream().filter(a -> a.tipo() == AlertaTipo.CRITICO).count(),
                todos.stream().filter(a -> a.tipo() == AlertaTipo.ATENCAO).count(),
                todos.stream().filter(a -> a.tipo() == AlertaTipo.INFO).count()
        );
    }

    private List<AlertaResponse> gerarAlertasDoItem(ItemEstoque item) {
        List<AlertaResponse> alertas = new ArrayList<>();
        String hospitalNome = item.getHospital().getNome();

        // Alerta de nivel de estoque
        NivelEstoque nivel = item.calcularNivel();
        if (nivel == NivelEstoque.CRITICO) {
            alertas.add(new AlertaResponse(item.getId(), item.getNome(), AlertaTipo.CRITICO,
                    "Estoque critico: %d %s (minimo %d)".formatted(
                            item.getQuantidadeAtual(), unidade(item), item.getQuantidadeMinima()),
                    hospitalNome, item.getLocalArmazenamento()));
        } else if (nivel == NivelEstoque.ATENCAO) {
            alertas.add(new AlertaResponse(item.getId(), item.getNome(), AlertaTipo.ATENCAO,
                    "Estoque em atencao: %d %s (minimo %d)".formatted(
                            item.getQuantidadeAtual(), unidade(item), item.getQuantidadeMinima()),
                    hospitalNome, item.getLocalArmazenamento()));
        }

        // Alerta de validade 
        if (item.isVencido()) {
            alertas.add(new AlertaResponse(item.getId(), item.getNome(), AlertaTipo.CRITICO,
                    "Item vencido em " + item.getValidade(),
                    hospitalNome, item.getLocalArmazenamento()));
        } else if (item.isValidadeProxima()) {
            long dias = item.diasParaVencer();
            AlertaTipo tipoValidade = dias <= DIAS_LIMITE_ATENCAO_VALIDADE ? AlertaTipo.ATENCAO : AlertaTipo.INFO;
            alertas.add(new AlertaResponse(item.getId(), item.getNome(), tipoValidade,
                    "Validade proxima: vence em " + dias + " dia(s)",
                    hospitalNome, item.getLocalArmazenamento()));
        }

        return alertas;
    }

    private String unidade(ItemEstoque item) {
        return item.getUnidadeMedida() == null || item.getUnidadeMedida().isBlank()
                ? "un."
                : item.getUnidadeMedida();
    }
}
