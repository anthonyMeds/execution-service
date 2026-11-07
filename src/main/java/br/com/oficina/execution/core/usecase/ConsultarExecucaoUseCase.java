package br.com.oficina.execution.core.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;

/** Consultas. Separado das escritas porque nao publica evento nem muda estado. */
@Service
public class ConsultarExecucaoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final BuscadorDeExecucao buscador;

	public ConsultarExecucaoUseCase(ExecucaoRepositoryPort execucoes, BuscadorDeExecucao buscador) {
		this.execucoes = execucoes;
		this.buscador = buscador;
	}

	public Execucao porOrdemServico(Long ordemServicoId) {
		return buscador.porOrdemServico(ordemServicoId);
	}

	public List<Execucao> fila() {
		return execucoes.fila();
	}
}
