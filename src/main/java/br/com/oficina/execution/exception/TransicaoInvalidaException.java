package br.com.oficina.execution.exception;

/** Tentativa de mudar o estado de um jeito que a regra nao permite. Vira HTTP 409. */
public class TransicaoInvalidaException extends RuntimeException {

	public TransicaoInvalidaException(String mensagem) {
		super(mensagem);
	}
}
