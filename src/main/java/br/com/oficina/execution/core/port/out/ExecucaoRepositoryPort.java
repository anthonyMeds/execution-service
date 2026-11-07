package br.com.oficina.execution.core.port.out;

import java.util.List;
import java.util.Optional;

import br.com.oficina.execution.core.domain.Execucao;

/**
 * Porta de saida. O dominio declara o que precisa; quem implementa e o adapter
 * Mongo. Trocar Mongo por outra coisa nao toca em nada de core/.
 */
public interface ExecucaoRepositoryPort {

	Execucao salvar(Execucao execucao);

	Optional<Execucao> porOrdemServico(Long ordemServicoId);

	/** Usado para idempotencia: se ja existe execucao com este sagaId, o comando repetiu. */
	Optional<Execucao> porSagaId(String sagaId);

	/** Fila de espera, mais antiga primeiro. */
	List<Execucao> fila();
}
