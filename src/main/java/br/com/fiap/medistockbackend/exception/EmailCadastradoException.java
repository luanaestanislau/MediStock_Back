package br.com.fiap.medistockbackend.exception;

public class EmailCadastradoException extends RuntimeException {
    public EmailCadastradoException(String email) {
        super("Ja existe um usuario cadastrado com o e-mail: " + email);
    }
}
