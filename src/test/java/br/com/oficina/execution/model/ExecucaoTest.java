package br.com.oficina.execution.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.oficina.execution.exception.TransicaoInvalidaException;

/** As regras de mudanca de estado. Nao precisa de Spring nem de banco. */
class ExecucaoTest {

	private Execucao novaExecucao() {
		return new Execucao(
				"saga-1",
				1042L,
				"OS-2026-000123",
				List.of(new ServicoExecucao(7L, "Troca de pastilhas"), new ServicoExecucao(3L, "Alinhamento")),
				List.of(new PecaRequisitada(55L, 4)));
	}

	@Test
	@DisplayName("nasce NA_FILA e ja com a primeira linha do historico")
	void nasceNaFila() {
		Execucao execucao = novaExecucao();

		assertEquals(StatusExecucao.NA_FILA, execucao.getStatus());
		assertEquals(1, execucao.getHistorico().size());
		assertEquals("ENFILEIRADA", execucao.getHistorico().get(0).evento());
	}

	@Test
	@DisplayName("nao existe execucao sem servico")
	void exigeAoMenosUmServico() {
		assertThrows(IllegalArgumentException.class,
				() -> new Execucao("saga-2", 1L, "OS-1", List.of(), List.of()));
	}

	@Test
	@DisplayName("iniciar leva para EM_EXECUCAO e guarda o mecanico")
	void iniciar() {
		Execucao execucao = novaExecucao();

		execucao.iniciar("Carlos");

		assertEquals(StatusExecucao.EM_EXECUCAO, execucao.getStatus());
		assertEquals("Carlos", execucao.getMecanicoResponsavel());
	}

	@Test
	@DisplayName("nao da para iniciar duas vezes")
	void naoIniciaDuasVezes() {
		Execucao execucao = novaExecucao();
		execucao.iniciar("Carlos");

		assertThrows(TransicaoInvalidaException.class, () -> execucao.iniciar("Ana"));
	}

	@Test
	@DisplayName("nao finaliza enquanto houver servico pendente")
	void naoFinalizaComServicoPendente() {
		Execucao execucao = novaExecucao();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");

		TransicaoInvalidaException erro = assertThrows(TransicaoInvalidaException.class, execucao::finalizar);
		assertTrue(erro.getMessage().contains("pendentes"));
	}

	@Test
	@DisplayName("finaliza quando todos os servicos estao concluidos")
	void finalizar() {
		Execucao execucao = novaExecucao();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");
		execucao.finalizarServico(3L, "ok");

		execucao.finalizar();

		assertEquals(StatusExecucao.FINALIZADA, execucao.getStatus());
		assertTrue(execucao.todosOsServicosConcluidos());
	}

	@Test
	@DisplayName("so da para finalizar servico com a execucao em andamento")
	void finalizarServicoExigeExecucaoIniciada() {
		Execucao execucao = novaExecucao();

		assertThrows(TransicaoInvalidaException.class, () -> execucao.finalizarServico(7L, null));
	}

	@Test
	@DisplayName("servico que nao e da execucao, ou ja concluido, e recusado")
	void finalizarServicoInvalido() {
		Execucao execucao = novaExecucao();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");

		assertThrows(TransicaoInvalidaException.class, () -> execucao.finalizarServico(999L, null));
		assertThrows(TransicaoInvalidaException.class, () -> execucao.finalizarServico(7L, null));
	}

	@Test
	@DisplayName("falta de peca leva para AGUARDANDO_ITENS, e dali ainda da para iniciar")
	void aguardarItens() {
		Execucao execucao = novaExecucao();

		execucao.aguardarItens(List.of(55L));
		assertEquals(StatusExecucao.AGUARDANDO_ITENS, execucao.getStatus());

		execucao.iniciar("Carlos");
		assertEquals(StatusExecucao.EM_EXECUCAO, execucao.getStatus());
	}

	@Test
	@DisplayName("falhar encerra, cancela os servicos em aberto e guarda o motivo")
	void falhar() {
		Execucao execucao = novaExecucao();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");

		execucao.falhar("peca com defeito");

		assertEquals(StatusExecucao.FALHOU, execucao.getStatus());
		assertEquals("peca com defeito", execucao.getMotivoEncerramento());
		assertEquals(StatusServicoExecucao.CONCLUIDO, execucao.getServicos().get(0).getStatus());
		assertEquals(StatusServicoExecucao.CANCELADO, execucao.getServicos().get(1).getStatus());
	}

	@Test
	@DisplayName("execucao encerrada nao aceita mais nada")
	void encerradaNaoMuda() {
		Execucao execucao = novaExecucao();
		execucao.cancelar("cliente desistiu");

		assertEquals(StatusExecucao.CANCELADA, execucao.getStatus());
		assertThrows(TransicaoInvalidaException.class, () -> execucao.iniciar("Carlos"));
		assertThrows(TransicaoInvalidaException.class, () -> execucao.falhar("x"));
		assertThrows(TransicaoInvalidaException.class, () -> execucao.aguardarItens(List.of(1L)));
	}

	@Test
	@DisplayName("duracao e zero enquanto nao terminou, e nunca negativa depois")
	void duracao() {
		Execucao execucao = novaExecucao();
		assertEquals(0L, execucao.duracaoSegundos());
		assertEquals(0L, execucao.getServicos().get(0).duracaoSegundos());

		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, null);
		execucao.finalizarServico(3L, null);
		execucao.finalizar();

		assertTrue(execucao.duracaoSegundos() >= 0);
		assertTrue(execucao.getServicos().get(0).duracaoSegundos() >= 0);
	}

	@Test
	@DisplayName("peca precisa de item e de quantidade positiva")
	void pecaInvalida() {
		assertThrows(IllegalArgumentException.class, () -> new PecaRequisitada(null, 1));
		assertThrows(IllegalArgumentException.class, () -> new PecaRequisitada(55L, 0));
	}

	@Test
	@DisplayName("cada tipo de movimentacao pesa de um jeito no saldo")
	void efeitoDaMovimentacaoNoSaldo() {
		assertEquals(10, TipoMovimentacao.ENTRADA.efeitoNoSaldo(10));
		assertEquals(-4, TipoMovimentacao.RESERVA.efeitoNoSaldo(4));
		assertEquals(0, TipoMovimentacao.BAIXA.efeitoNoSaldo(4));
		assertEquals(4, TipoMovimentacao.ESTORNO.efeitoNoSaldo(4));
		assertEquals(-4, new MovimentacaoEstoque("saga-1", 55L, 4, TipoMovimentacao.RESERVA).getEfeitoNoSaldo());
	}
}
