package br.com.fiap.medistockbackend.dto;

public record ResumoEstoqueResponse(
        long totalItens,
        long itensCriticos,
        long itensAtencao,
        long itensValidadeProxima
) {}
