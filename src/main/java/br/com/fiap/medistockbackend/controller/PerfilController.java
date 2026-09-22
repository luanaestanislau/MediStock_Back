package br.com.fiap.medistockbackend.controller;

import br.com.fiap.medistockbackend.dto.MatriculaResponse;
import br.com.fiap.medistockbackend.exception.CredenciaisInvalidasException;
import br.com.fiap.medistockbackend.model.Usuario;
import br.com.fiap.medistockbackend.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
@Tag(name = "Perfil", description = "Tela de matricula exibida logo apos cadastro/login")
public class PerfilController {

    private final UsuarioRepository usuarioRepository;

    @GetMapping("/matricula")
    @Operation(summary = "Retorna o nome real do usuario logado + dados mockados de matricula/departamento/cargo")
    public ResponseEntity<MatriculaResponse> matricula(Authentication authentication) {
        String emailAutenticado = authentication.getName();

        Usuario usuario = usuarioRepository.findByEmailInstitucionalIgnoreCase(emailAutenticado)
                .orElseThrow(CredenciaisInvalidasException::new);

        return ResponseEntity.ok(MatriculaResponse.mockPara(usuario.getNomeCompleto()));
    }
}