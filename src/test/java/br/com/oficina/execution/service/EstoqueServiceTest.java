package br.com.oficina.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.oficina.execution.model.MovimentacaoEstoque;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.TipoMovimentacao;
import br.com.oficina.execution.repository.MovimentacaoEstoqueRepository;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

	@Mock
	private MovimentacaoEstoqueRepository movimentacaoRepository;

	@InjectMocks
	private EstoqueService estoqueService;

	private final List<PecaRequisitada> quatroPastilhas = List.of(new PecaRequisitada(55L, 4));

	private void saldoDoItem55(int saldo) {
		when(movimentacaoRepository.findByItemId(55L)).thenReturn(saldo == 0
				? List.of()
				: List.of(new MovimentacaoEstoque("NF-1", 55L, saldo, TipoMovimentacao.ENTRADA)));
	}

	private MovimentacaoEstoque movimentacaoGravada() {
		ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
		verify(movimentacaoRepository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("o saldo e a soma das movimentacoes")
	void saldoESoma() {
		when(movimentacaoRepository.findByItemId(55L)).thenReturn(List.of(
				new MovimentacaoEstoque("NF-1", 55L, 10, TipoMovimentacao.ENTRADA),
				new MovimentacaoEstoque("saga-1", 55L, 4, TipoMovimentacao.RESERVA),
				new MovimentacaoEstoque("saga-1", 55L, 4, TipoMovimentacao.BAIXA),
				new MovimentacaoEstoque("saga-2", 55L, 3, TipoMovimentacao.RESERVA),
				new MovimentacaoEstoque("saga-2", 55L, 3, TipoMovimentacao.ESTORNO)));

		assertEquals(6, estoqueService.saldoDe(55L));
	}

	@Test
	@DisplayName("entrada nova e gravada")
	void entradaNova() {
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("NF-900", 55L, TipoMovimentacao.ENTRADA))
				.thenReturn(false);

		estoqueService.registrarEntrada("NF-900", 55L, 10);

		MovimentacaoEstoque gravada = movimentacaoGravada();
		assertEquals(TipoMovimentacao.ENTRADA, gravada.getTipo());
		assertEquals(10, gravada.getEfeitoNoSaldo());
		assertEquals("NF-900", gravada.getReferencia());
	}

	@Test
	@DisplayName("a mesma nota fiscal lancada duas vezes nao dobra o estoque")
	void entradaRepetida() {
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("NF-900", 55L, TipoMovimentacao.ENTRADA))
				.thenReturn(true);

		estoqueService.registrarEntrada("NF-900", 55L, 10);

		verify(movimentacaoRepository, never()).save(any());
	}

	@Test
	@DisplayName("entrada sem referencia ou com quantidade invalida e recusada")
	void entradaInvalida() {
		assertThrows(IllegalArgumentException.class, () -> estoqueService.registrarEntrada(" ", 55L, 10));
		assertThrows(IllegalArgumentException.class, () -> estoqueService.registrarEntrada(null, 55L, 10));
		assertThrows(IllegalArgumentException.class, () -> estoqueService.registrarEntrada("NF-1", 55L, 0));
	}

	@Test
	@DisplayName("reserva quando ha saldo")
	void reservaComSaldo() {
		saldoDoItem55(10);

		List<Long> emFalta = estoqueService.reservar("saga-1", quatroPastilhas);

		assertTrue(emFalta.isEmpty());
		assertEquals(TipoMovimentacao.RESERVA, movimentacaoGravada().getTipo());
	}

	@Test
	@DisplayName("sem saldo, nao reserva e devolve o item que faltou")
	void reservaSemSaldo() {
		saldoDoItem55(1);

		List<Long> emFalta = estoqueService.reservar("saga-1", quatroPastilhas);

		assertEquals(List.of(55L), emFalta);
		verify(movimentacaoRepository, never()).save(any());
	}

	@Test
	@DisplayName("o que ja foi reservado para a saga nao e reservado de novo")
	void reservaRepetida() {
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("saga-1", 55L, TipoMovimentacao.RESERVA))
				.thenReturn(true);

		List<Long> emFalta = estoqueService.reservar("saga-1", quatroPastilhas);

		assertTrue(emFalta.isEmpty());
		verify(movimentacaoRepository, never()).save(any());
	}

	@Test
	@DisplayName("dar baixa grava o consumo, uma vez so")
	void darBaixa() {
		estoqueService.darBaixa("saga-1", quatroPastilhas);

		assertEquals(TipoMovimentacao.BAIXA, movimentacaoGravada().getTipo());
	}

	@Test
	@DisplayName("devolver estorna o que estava reservado")
	void devolverReservado() {
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("saga-1", 55L, TipoMovimentacao.RESERVA))
				.thenReturn(true);
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("saga-1", 55L, TipoMovimentacao.ESTORNO))
				.thenReturn(false);

		estoqueService.devolver("saga-1", quatroPastilhas);

		assertEquals(TipoMovimentacao.ESTORNO, movimentacaoGravada().getTipo());
	}

	@Test
	@DisplayName("devolver nao inventa estoque: o que nunca foi reservado nao e estornado")
	void devolverNaoReservado() {
		when(movimentacaoRepository.existsByReferenciaAndItemIdAndTipo("saga-1", 55L, TipoMovimentacao.RESERVA))
				.thenReturn(false);

		estoqueService.devolver("saga-1", quatroPastilhas);

		verify(movimentacaoRepository, never()).save(any());
	}
}
