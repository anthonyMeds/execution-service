package br.com.oficina.execution.core.domain;

import java.time.Instant;

/**
 * Registro imutavel de movimentacao. A chave de idempotencia e o trio
 * (sagaId, itemId, tipo): a mesma mensagem reprocessada encontra o registro
 * e nao cria outro.
 */
public record MovimentacaoEstoque(
		String id,
		String sagaId,
		Long itemId,
		int quantidade,
		TipoMovimentacao tipo,
		Instant em
) {

	public static MovimentacaoEstoque nova(String sagaId, Long itemId, int quantidade, TipoMovimentacao tipo) {
		return new MovimentacaoEstoque(null, sagaId, itemId, quantidade, tipo, Instant.now());
	}

	public int efeitoNoSaldo() {
		return tipo.aplicarA(quantidade);
	}
}
