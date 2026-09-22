package br.com.fiap.medistockbackend.dto;

public record MatriculaResponse(
        String nomeCompleto,
        String matricula,
        String departamento,
        String cargo,
        String registroProfissional,
        String perfil,
        String hospital
) {
    public static MatriculaResponse mockPara(String nomeCompleto) {
        return new MatriculaResponse(
                nomeCompleto,
                "MED-2026-089",
                "Farmacia Hospitalar e UTI",
                "Gestora de Insumos",
                "CRM-SP 987654",
                "GESTOR",
                "Hospital Central MediStock"
        );
    }
}
