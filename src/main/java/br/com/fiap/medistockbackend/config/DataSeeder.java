package br.com.fiap.medistockbackend.config;

import br.com.fiap.medistockbackend.model.Entrega;
import br.com.fiap.medistockbackend.model.StatusLogistico;
import br.com.fiap.medistockbackend.repository.EntregaRepository;
import br.com.fiap.medistockbackend.repository.HospitalRepository;
import br.com.fiap.medistockbackend.repository.ItemEstoqueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
public class DataSeeder {

    /** Cenário demonstrativo visível na Home e em Logística, sem duplicar dados em reinicializações. */
    @Bean
    CommandLineRunner criarEntregaDemonstrativa(
            EntregaRepository entregaRepository,
            ItemEstoqueRepository itemEstoqueRepository,
            HospitalRepository hospitalRepository) {
        return args -> {
            if (entregaRepository.count() > 0) {
                return;
            }

            var item = itemEstoqueRepository.findAll().stream().findFirst();
            var hospital = hospitalRepository.findAll().stream().findFirst();
            if (item.isEmpty() || hospital.isEmpty()) {
                return;
            }

            entregaRepository.save(Entrega.builder()
                    .itemEstoque(item.get())
                    .hospitalDestino(hospital.get())
                    .quantidade(120)
                    .dataPrevista(LocalDate.now())
                    .transportadora("MediSupply Express")
                    .status(StatusLogistico.EM_ROTA)
                    .build());
        };
    }
}
