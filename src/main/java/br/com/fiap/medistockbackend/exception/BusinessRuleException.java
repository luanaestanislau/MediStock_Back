package br.com.fiap.medistockbackend.exception;

public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String mensagem) {
        super(mensagem);
    }
}
