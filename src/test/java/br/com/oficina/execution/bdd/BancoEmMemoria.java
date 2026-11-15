package br.com.oficina.execution.bdd;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.MovimentacaoEstoque;
import br.com.oficina.execution.model.StatusExecucao;
import br.com.oficina.execution.model.TipoMovimentacao;
import br.com.oficina.execution.repository.ExecucaoRepository;
import br.com.oficina.execution.repository.MovimentacaoEstoqueRepository;

/**
 * Repositorios de mentira que guardam em memoria, para o BDD rodar sem Mongo.
 *
 * Sao mocks do Mockito programados para se comportar como um banco: o que e
 * salvo fica guardado numa lista, e as consultas leem dessa lista.
 */
final class BancoEmMemoria {

	private BancoEmMemoria() {
	}

	static ExecucaoRepository execucaoRepository() {
		Map<Long, Execucao> banco = new LinkedHashMap<>();
		ExecucaoRepository repository = mock(ExecucaoRepository.class);

		when(repository.save(any(Execucao.class))).thenAnswer(chamada -> {
			Execucao execucao = chamada.getArgument(0);
			banco.put(execucao.getOrdemServicoId(), execucao);
			return execucao;
		});
		when(repository.findByOrdemServicoId(anyLong())).thenAnswer(chamada -> {
			Long ordemServicoId = chamada.getArgument(0);
			return Optional.ofNullable(banco.get(ordemServicoId));
		});
		when(repository.findBySagaId(anyString())).thenAnswer(chamada -> {
			String sagaId = chamada.getArgument(0);
			return banco.values().stream().filter(e -> e.getSagaId().equals(sagaId)).findFirst();
		});
		when(repository.findByStatusInOrderByCriadaEmAsc(any())).thenAnswer(chamada -> {
			Collection<StatusExecucao> status = chamada.getArgument(0);
			return banco.values().stream().filter(e -> status.contains(e.getStatus())).toList();
		});
		return repository;
	}

	static MovimentacaoEstoqueRepository movimentacaoRepository() {
		List<MovimentacaoEstoque> banco = new ArrayList<>();
		MovimentacaoEstoqueRepository repository = mock(MovimentacaoEstoqueRepository.class);

		when(repository.save(any(MovimentacaoEstoque.class))).thenAnswer(chamada -> {
			MovimentacaoEstoque movimentacao = chamada.getArgument(0);
			banco.add(movimentacao);
			return movimentacao;
		});
		when(repository.existsByReferenciaAndItemIdAndTipo(anyString(), anyLong(), any())).thenAnswer(chamada -> {
			String referencia = chamada.getArgument(0);
			Long itemId = chamada.getArgument(1);
			TipoMovimentacao tipo = chamada.getArgument(2);
			return banco.stream().anyMatch(m -> m.getReferencia().equals(referencia)
					&& m.getItemId().equals(itemId)
					&& m.getTipo() == tipo);
		});
		when(repository.findByItemId(anyLong())).thenAnswer(chamada -> {
			Long itemId = chamada.getArgument(0);
			return banco.stream().filter(m -> m.getItemId().equals(itemId)).toList();
		});
		return repository;
	}
}
