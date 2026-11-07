package br.com.oficina.execution.core.port.out;

import br.com.oficina.execution.core.domain.MovimentacaoEstoque;
import br.com.oficina.execution.core.domain.TipoMovimentacao;

/**
 * Estoque append-only: nunca se escreve saldo por cima, soma-se a movimentacao.
 * E a saida escolhida para o ponto fraco de saldo em banco de documentos --
 * em vez de depender de transacao multi-documento, cada movimentacao e um fato
 * imutavel e o saldo e derivado.
 */
public interface MovimentacaoEstoqueRepositoryPort {

	MovimentacaoEstoque registrar(MovimentacaoEstoque movimentacao);

	/** Chave de idempotencia: o mesmo sagaId nao debita o mesmo item duas vezes. */
	boolean jaRegistrada(String sagaId, Long itemId, TipoMovimentacao tipo);

	int saldoDe(Long itemId);
}
