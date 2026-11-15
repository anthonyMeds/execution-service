package br.com.oficina.execution.model;

/**
 * O saldo de uma peca nao e um campo que se escreve por cima: e a soma das
 * movimentacoes. Cada tipo diz o quanto pesa nessa soma.
 */
public enum TipoMovimentacao {

	/** Peca chegou do fornecedor. Soma. */
	ENTRADA(1),

	/** Peca separada para uma OS. Subtrai. */
	RESERVA(-1),

	/** Peca usada de fato. Nao mexe no saldo: a reserva ja tinha subtraido. */
	BAIXA(0),

	/** Reserva desfeita, a peca volta para a prateleira. Soma. */
	ESTORNO(1);

	private final int sinal;

	TipoMovimentacao(int sinal) {
		this.sinal = sinal;
	}

	public int efeitoNoSaldo(int quantidade) {
		return sinal * quantidade;
	}
}
