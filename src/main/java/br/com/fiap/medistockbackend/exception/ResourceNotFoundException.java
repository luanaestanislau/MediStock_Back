package br.com.fiap.medistockbackend.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String mensagem) {
        super(mensagem);
    }
}
