package br.com.fiap.medistockbackend.exception;

public class TransferenciaDesnecessariaException extends RuntimeException{
    public TransferenciaDesnecessariaException(String itemNome) {
        super("O item '" + itemNome + "' ja esta no hospital ideal segundo a IA -- nenhuma transferencia e necessaria");
    }
}
