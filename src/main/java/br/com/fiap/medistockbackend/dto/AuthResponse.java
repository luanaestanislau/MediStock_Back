package br.com.fiap.medistockbackend.dto;

public record AuthResponse(

    String token,
    String tipo,          // "Bearer"
    long expiraEmMinutos,
    UsuarioResponse usuario
    
) {}

