package br.com.oficina.execution.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TipoMovimentacaoTest {

	@Test
	@DisplayName("reserva debita, estorno devolve, baixa nao mexe no saldo ja reservado")
	void efeitoNoSaldo() {
		assertEquals(-4, TipoMovimentacao.RESERVA.aplicarA(4));
		assertEquals(4, TipoMovimentacao.ESTORNO.aplicarA(4));
		assertEquals(0, TipoMovimentacao.BAIXA.aplicarA(4));
	}

	@Test
	@DisplayName("o saldo e a soma das movimentacoes: reserva seguida de estorno volta a zero")
	void reservaEEstornoSeCancelam() {
		int saldo = TipoMovimentacao.RESERVA.aplicarA(4) + TipoMovimentacao.ESTORNO.aplicarA(4);

		assertEquals(0, saldo);
	}
}
