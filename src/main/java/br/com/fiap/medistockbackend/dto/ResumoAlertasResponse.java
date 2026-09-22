package br.com.fiap.medistockbackend.dto;

public record ResumoAlertasResponse(
        long criticos,
        long atencao,
        long info
) {}
