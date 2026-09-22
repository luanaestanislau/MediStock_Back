package br.com.fiap.medistockbackend.exception;

public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("E-mail institucional ou senha invalidos");
    }
}
