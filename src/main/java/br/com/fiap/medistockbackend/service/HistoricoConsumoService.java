package br.com.fiap.medistockbackend.service;

import br.com.fiap.medistockbackend.dto.IaDtos.HistoricoConsumoRequest;
import br.com.fiap.medistockbackend.model.Hospital;
import br.com.fiap.medistockbackend.model.HistoricoConsumo;
import br.com.fiap.medistockbackend.model.ItemEstoque;
import br.com.fiap.medistockbackend.repository.HistoricoConsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class HistoricoConsumoService {

    private final HistoricoConsumoRepository historicoConsumoRepository;
    private final ItemEstoqueService itemEstoqueService;
    private final HospitalService hospitalService;

    @Transactional
    public void registrar(HistoricoConsumoRequest request) {
        ItemEstoque item = itemEstoqueService.buscarEntidade(request.itemEstoqueId());
        Hospital hospital = hospitalService.buscarEntidade(request.hospitalId());

        HistoricoConsumo historico = HistoricoConsumo.builder()
                .itemEstoque(item)
                .hospital(hospital)
                .mesReferencia(parseMes(request.mesReferencia()))
                .quantidadeConsumida(request.quantidadeConsumida())
                .build();

        historicoConsumoRepository.save(historico);
    }

    private LocalDate parseMes(String valor) {
        if (valor.length() == 7) { 
            return LocalDate.parse(valor + "-01");
        }
        return LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}

