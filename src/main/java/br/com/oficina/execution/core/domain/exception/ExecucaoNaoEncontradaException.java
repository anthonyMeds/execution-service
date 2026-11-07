package br.com.oficina.execution.core.domain.exception;

/** Vira HTTP 404. */
public class ExecucaoNaoEncontradaException extends RuntimeException {

	public ExecucaoNaoEncontradaException(String mensagem) {
		super(mensagem);
	}

	public static ExecucaoNaoEncontradaException porOrdemServico(Long ordemServicoId) {
		return new ExecucaoNaoEncontradaException(
				"nao existe execucao para a ordem de servico %d".formatted(ordemServicoId));
	}
}
