package br.com.fiap.medistockbackend.service;

import br.com.fiap.medistockbackend.dto.LogisticaDtos.*;
import br.com.fiap.medistockbackend.exception.ResourceNotFoundException;
import br.com.fiap.medistockbackend.model.*;
import br.com.fiap.medistockbackend.repository.EntregaRepository;
import br.com.fiap.medistockbackend.repository.TransferenciaRepository;
import br.com.fiap.medistockbackend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogisticaService {

    private final EntregaRepository entregaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ItemEstoqueService itemEstoqueService;
    private final HospitalService hospitalService;

    // Entregas

    public List<EntregaResponse> listarEntregas() {
        return entregaRepository.findAll().stream().map(EntregaResponse::fromEntity).toList();
    }

    @Transactional
    public EntregaResponse criarEntrega(EntregaRequest request) {
        ItemEstoque item = itemEstoqueService.buscarEntidade(request.itemEstoqueId());
        Hospital destino = hospitalService.buscarEntidade(request.hospitalDestinoId());

        Entrega entrega = Entrega.builder()
                .itemEstoque(item)
                .hospitalDestino(destino)
                .quantidade(request.quantidade())
                .dataPrevista(request.dataPrevista())
                .transportadora(request.transportadora())
                .status(StatusLogistico.EM_ROTA)
                .build();

        return EntregaResponse.fromEntity(entregaRepository.save(entrega));
    }

    @Transactional
    public EntregaResponse atualizarStatusEntrega(Long id, StatusLogistico novoStatus) {
        Entrega entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega nao encontrada: id " + id));
        entrega.setStatus(novoStatus);
        return EntregaResponse.fromEntity(entrega);
    }

    // Transferencias 

    public List<TransferenciaResponse> listarTransferencias() {
        return transferenciaRepository.findAll().stream().map(TransferenciaResponse::fromEntity).toList();
    }

    @Transactional
    public TransferenciaResponse criarTransferencia(TransferenciaRequest request) {
        ItemEstoque item = itemEstoqueService.buscarEntidade(request.itemEstoqueId());
        Hospital origem = hospitalService.buscarEntidade(request.hospitalOrigemId());
        Hospital destino = hospitalService.buscarEntidade(request.hospitalDestinoId());

        double distanciaKm = GeoUtils.distanciaKm(
                origem.getLatitude(), origem.getLongitude(),
                destino.getLatitude(), destino.getLongitude());
        double tempoMin = GeoUtils.tempoEstimadoMinutos(distanciaKm);

        Transferencia transferencia = Transferencia.builder()
                .itemEstoque(item)
                .hospitalOrigem(origem)
                .hospitalDestino(destino)
                .quantidade(request.quantidade())
                .distanciaKm(distanciaKm)
                .tempoEstimadoMinutos(tempoMin)
                .motivo(request.motivo())
                .geradoPorIa(false)
                .status(StatusLogistico.PENDENTE)
                .build();

        return TransferenciaResponse.fromEntity(transferenciaRepository.save(transferencia));
    }

    @Transactional
    public TransferenciaResponse atualizarStatusTransferencia(Long id, StatusLogistico novoStatus) {
        Transferencia transferencia = transferenciaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transferencia nao encontrada: id " + id));
        transferencia.setStatus(novoStatus);
        return TransferenciaResponse.fromEntity(transferencia);
    }

    @Transactional
    public Transferencia registrarTransferenciaDaIa(ItemEstoque item, Hospital origem, Hospital destino,
                                                      int quantidade, double distanciaKm, double tempoMin,
                                                      String motivo) {
        Transferencia transferencia = Transferencia.builder()
                .itemEstoque(item)
                .hospitalOrigem(origem)
                .hospitalDestino(destino)
                .quantidade(quantidade)
                .distanciaKm(distanciaKm)
                .tempoEstimadoMinutos(tempoMin)
                .motivo(motivo)
                .geradoPorIa(true)
                .status(StatusLogistico.PENDENTE)
                .build();

        return transferenciaRepository.save(transferencia);
    }
}
