package br.com.oficina.execution.exception;

/** Nao existe execucao para a OS informada. Vira HTTP 404. */
public class ExecucaoNaoEncontradaException extends RuntimeException {

	public ExecucaoNaoEncontradaException(Long ordemServicoId) {
		super("nao existe execucao para a ordem de servico %d".formatted(ordemServicoId));
	}
}
