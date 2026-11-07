package br.com.oficina.execution.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.oficina.execution.core.domain.exception.TransicaoInvalidaException;

/**
 * O dominio e testavel sem Spring e sem banco. E o retorno pratico de manter as
 * regras em core/ e a persistencia no adapter.
 */
class ExecucaoTest {

	private Execucao umaExecucaoNaFila() {
		return Execucao.enfileirar(
				"saga-1",
				1042L,
				"OS-2026-000123",
				List.of(new ServicoExecucao(7L, "Troca de pastilhas"), new ServicoExecucao(3L, "Alinhamento")),
				List.of(new PecaRequisitada(55L, 4)));
	}

	@Test
	@DisplayName("entra na fila com NA_FILA e ja registra historico")
	void entraNaFila() {
		Execucao execucao = umaExecucaoNaFila();

		assertEquals(StatusExecucao.NA_FILA, execucao.getStatus());
		assertEquals(1, execucao.getHistorico().size());
		assertEquals("ENFILEIRADA", execucao.getHistorico().get(0).evento());
	}

	@Test
	@DisplayName("nao aceita execucao sem servico")
	void exigeAoMenosUmServico() {
		assertThrows(IllegalArgumentException.class, () ->
				Execucao.enfileirar("saga-2", 1L, "OS-1", List.of(), List.of()));
	}

	@Test
	@DisplayName("iniciar leva para EM_EXECUCAO e guarda o mecanico")
	void inicia() {
		Execucao execucao = umaExecucaoNaFila();

		execucao.iniciar("Carlos");

		assertEquals(StatusExecucao.EM_EXECUCAO, execucao.getStatus());
		assertEquals("Carlos", execucao.getMecanicoResponsavel().orElseThrow());
	}

	@Test
	@DisplayName("nao finaliza enquanto houver servico pendente")
	void naoFinalizaComServicoPendente() {
		Execucao execucao = umaExecucaoNaFila();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");

		TransicaoInvalidaException erro = assertThrows(TransicaoInvalidaException.class, execucao::finalizar);
		assertTrue(erro.getMessage().contains("pendentes"));
	}

	@Test
	@DisplayName("finaliza quando todos os servicos estao concluidos")
	void finaliza() {
		Execucao execucao = umaExecucaoNaFila();
		execucao.iniciar("Carlos");
		execucao.finalizarServico(7L, "ok");
		execucao.finalizarServico(3L, "ok");

		execucao.finalizar();

		assertEquals(StatusExecucao.FINALIZADA, execucao.getStatus());
		assertTrue(execucao.todosOsServicosConcluidos());
	}

	@Test
	@DisplayName("falta de peca leva para AGUARDANDO_ITENS, e dali ainda da para iniciar")
	void aguardaItens() {
		Execucao execucao = umaExecucaoNaFila();

		execucao.aguardarItens(List.of(55L));
		assertEquals(StatusExecucao.AGUARDANDO_ITENS, execucao.getStatus());

		execucao.iniciar("Carlos");
		assertEquals(StatusExecucao.EM_EXECUCAO, execucao.getStatus());
	}

	@Test
	@DisplayName("falhar cancela os servicos e marca o motivo")
	void falha() {
		Execucao execucao = umaExecucaoNaFila();
		execucao.iniciar("Carlos");

		execucao.falhar("peca danificada na montagem");

		assertEquals(StatusExecucao.FALHOU, execucao.getStatus());
		assertEquals("peca danificada na montagem", execucao.getMotivoEncerramento());
		assertTrue(execucao.getStatus().encerrado());
	}

	@Test
	@DisplayName("execucao encerrada nao aceita nova transicao")
	void encerradaNaoTransiciona() {
		Execucao execucao = umaExecucaoNaFila();
		execucao.cancelar("cliente desistiu");

		assertThrows(TransicaoInvalidaException.class, () -> execucao.iniciar("Carlos"));
	}

	@Test
	@DisplayName("servico que nao pertence a execucao e recusado")
	void servicoDesconhecido() {
		Execucao execucao = umaExecucaoNaFila();
		execucao.iniciar("Carlos");

		assertThrows(TransicaoInvalidaException.class, () -> execucao.finalizarServico(999L, null));
	}
}
