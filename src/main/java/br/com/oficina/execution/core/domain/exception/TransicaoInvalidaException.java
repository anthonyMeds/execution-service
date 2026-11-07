package br.com.oficina.execution.core.domain.exception;

/** Tentativa de transicao que a maquina de estados nao permite. Vira HTTP 409. */
public class TransicaoInvalidaException extends RuntimeException {

	public TransicaoInvalidaException(String mensagem) {
		super(mensagem);
	}
}
