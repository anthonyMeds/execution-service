package br.com.oficina.execution.core.usecase;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;

/** Evita repetir o mesmo orElseThrow em cada caso de uso. */
@Service
public class BuscadorDeExecucao {

	private final ExecucaoRepositoryPort execucoes;

	public BuscadorDeExecucao(ExecucaoRepositoryPort execucoes) {
		this.execucoes = execucoes;
	}

	public Execucao porOrdemServico(Long ordemServicoId) {
		return execucoes.porOrdemServico(ordemServicoId)
				.orElseThrow(() -> ExecucaoNaoEncontradaException.porOrdemServico(ordemServicoId));
	}
}
