package br.com.oficina.execution.core.domain;

/**
 * O saldo de estoque nao e uma coluna que se escreve por cima: e a soma das
 * movimentacoes. RESERVA e BAIXA diminuem, ESTORNO devolve. Cada movimentacao
 * carrega o sagaId, entao reprocessar a mesma mensagem nao debita duas vezes.
 */
public enum TipoMovimentacao {

	RESERVA(-1),
	BAIXA(0),
	ESTORNO(1);

	private final int sinal;

	TipoMovimentacao(int sinal) {
		this.sinal = sinal;
	}

	public int aplicarA(int quantidade) {
		return sinal * quantidade;
	}
}
