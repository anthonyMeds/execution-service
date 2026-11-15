package br.com.oficina.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.exception.TransicaoInvalidaException;
import br.com.oficina.execution.messaging.Envelope;
import br.com.oficina.execution.messaging.EventoPublisher;
import br.com.oficina.execution.messaging.TipoEvento;
import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.ItemEmFalta;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.model.StatusExecucao;
import br.com.oficina.execution.repository.ExecucaoRepository;

/**
 * Testa o service isolado: repositorio, estoque e publicador sao de mentira
 * (mocks), entao nada aqui precisa de Mongo nem de fila.
 */
@ExtendWith(MockitoExtension.class)
class ExecucaoServiceTest {

	private static final Long OS = 1042L;
	private static final List<PecaRequisitada> PECAS = List.of(new PecaRequisitada(55L, 4));
	private static final ItemEmFalta FALTA_PASTILHA = new ItemEmFalta(55L, 4, 1);

	@Mock
	private ExecucaoRepository execucaoRepository;

	@Mock
	private EstoqueService estoqueService;

	@Mock
	private EventoPublisher eventoPublisher;

	@InjectMocks
	private ExecucaoService execucaoService;

	private List<ServicoExecucao> servicos() {
		return List.of(new ServicoExecucao(7L, "Troca de pastilhas"), new ServicoExecucao(3L, "Alinhamento"));
	}

	private Execucao execucaoNaFila() {
		return new Execucao("saga-1", OS, "OS-2026-000123", servicos(), PECAS);
	}

	private void existeNoBanco(Execucao execucao) {
		when(execucaoRepository.findByOrdemServicoId(OS)).thenReturn(Optional.of(execucao));
	}

	private void salvarDevolveOQueRecebeu() {
		when(execucaoRepository.save(any(Execucao.class))).thenAnswer(chamada -> chamada.getArgument(0));
	}

	private Envelope eventoPublicado() {
		ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
		verify(eventoPublisher).publicar(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("enfileirar com estoque: fica NA_FILA e nao publica nada")
	void enfileirarComEstoque() {
		salvarDevolveOQueRecebeu();

		Execucao execucao = execucaoService.enfileirar("saga-1", OS, "OS-2026-000123", servicos(), PECAS);

		assertEquals(StatusExecucao.NA_FILA, execucao.getStatus());
		verify(estoqueService).reservar("saga-1", PECAS);
		verify(eventoPublisher, never()).publicar(any());
	}

	@Test
	@DisplayName("enfileirar sem estoque: fica AGUARDANDO_ITENS e avisa o OS Service")
	void enfileirarSemEstoque() {
		salvarDevolveOQueRecebeu();
		when(estoqueService.reservar("saga-1", PECAS)).thenReturn(List.of(FALTA_PASTILHA));

		Execucao execucao = execucaoService.enfileirar("saga-1", OS, "OS-2026-000123", servicos(), PECAS);

		assertEquals(StatusExecucao.AGUARDANDO_ITENS, execucao.getStatus());
		Envelope evento = eventoPublicado();
		assertEquals(TipoEvento.EXECUCAO_AGUARDANDO_ITENS, evento.tipo());
		assertEquals(List.of(FALTA_PASTILHA), evento.dados().get("itensEmFalta"));
		assertEquals("saga-1", evento.sagaId());
		assertNotNull(evento.messageId());
		assertEquals(Envelope.VERSAO_ATUAL, evento.versao().intValue());
	}

	@Test
	@DisplayName("a mesma mensagem entregue duas vezes nao cria duas execucoes")
	void enfileirarRepetido() {
		Execucao jaExiste = execucaoNaFila();
		when(execucaoRepository.findBySagaId("saga-1")).thenReturn(Optional.of(jaExiste));

		Execucao execucao = execucaoService.enfileirar("saga-1", OS, "OS-2026-000123", servicos(), PECAS);

		assertSame(jaExiste, execucao);
		verify(execucaoRepository, never()).save(any());
		verifyNoInteractions(estoqueService, eventoPublisher);
	}

	@Test
	@DisplayName("iniciar: vai para EM_EXECUCAO e publica ExecucaoIniciada")
	void iniciar() {
		existeNoBanco(execucaoNaFila());
		salvarDevolveOQueRecebeu();

		Execucao execucao = execucaoService.iniciar(OS, "Carlos");

		assertEquals(StatusExecucao.EM_EXECUCAO, execucao.getStatus());
		Envelope evento = eventoPublicado();
		assertEquals(TipoEvento.EXECUCAO_INICIADA, evento.tipo());
		assertEquals("saga-1", evento.sagaId());
		assertEquals("Carlos", evento.dados().get("mecanico"));
	}

	@Test
	@DisplayName("iniciar com peca ainda em falta e recusado, e nada e salvo")
	void iniciarAindaSemPeca() {
		Execucao aguardando = execucaoNaFila();
		aguardando.aguardarItens(List.of(55L));
		existeNoBanco(aguardando);
		when(estoqueService.reservar("saga-1", PECAS)).thenReturn(List.of(FALTA_PASTILHA));

		assertThrows(TransicaoInvalidaException.class, () -> execucaoService.iniciar(OS, "Carlos"));

		verify(execucaoRepository, never()).save(any());
		verifyNoInteractions(eventoPublisher);
	}

	@Test
	@DisplayName("finalizar servico: publica ServicoFinalizado")
	void finalizarServico() {
		Execucao emExecucao = execucaoNaFila();
		emExecucao.iniciar("Carlos");
		existeNoBanco(emExecucao);
		salvarDevolveOQueRecebeu();

		execucaoService.finalizarServico(OS, 7L, "ok");

		Envelope evento = eventoPublicado();
		assertEquals(TipoEvento.SERVICO_FINALIZADO, evento.tipo());
		assertEquals(7L, evento.dados().get("servicoId"));
	}

	@Test
	@DisplayName("finalizar: da baixa nas pecas e publica ExecucaoFinalizada")
	void finalizar() {
		Execucao pronta = execucaoNaFila();
		pronta.iniciar("Carlos");
		pronta.finalizarServico(7L, "ok");
		pronta.finalizarServico(3L, "ok");
		existeNoBanco(pronta);
		salvarDevolveOQueRecebeu();

		Execucao execucao = execucaoService.finalizar(OS);

		assertEquals(StatusExecucao.FINALIZADA, execucao.getStatus());
		verify(estoqueService).darBaixa("saga-1", PECAS);
		Envelope evento = eventoPublicado();
		assertEquals(TipoEvento.EXECUCAO_FINALIZADA, evento.tipo());
		assertEquals(List.of(7L, 3L), evento.dados().get("servicosExecutados"));
	}

	@Test
	@DisplayName("cancelar: devolve as pecas e nao publica evento")
	void cancelar() {
		existeNoBanco(execucaoNaFila());
		salvarDevolveOQueRecebeu();

		Execucao execucao = execucaoService.cancelar(OS, "cliente desistiu");

		assertEquals(StatusExecucao.CANCELADA, execucao.getStatus());
		verify(estoqueService).devolver("saga-1", PECAS);
		verifyNoInteractions(eventoPublisher);
	}

	@Test
	@DisplayName("cancelar duas vezes: a segunda nao faz nada e nao da erro")
	void cancelarDuasVezes() {
		Execucao jaCancelada = execucaoNaFila();
		jaCancelada.cancelar("cliente desistiu");
		existeNoBanco(jaCancelada);

		Execucao execucao = execucaoService.cancelar(OS, "cliente desistiu");

		assertSame(jaCancelada, execucao);
		verify(execucaoRepository, never()).save(any());
		verifyNoInteractions(estoqueService, eventoPublisher);
	}

	@Test
	@DisplayName("falhar: devolve as pecas e publica ExecucaoFalhou, o gatilho do estorno")
	void falhar() {
		Execucao emExecucao = execucaoNaFila();
		emExecucao.iniciar("Carlos");
		existeNoBanco(emExecucao);
		salvarDevolveOQueRecebeu();

		Execucao execucao = execucaoService.falhar(OS, "suspensao comprometida", "EXECUCAO_SERVICO");

		assertEquals(StatusExecucao.FALHOU, execucao.getStatus());
		verify(estoqueService).devolver("saga-1", PECAS);
		Envelope evento = eventoPublicado();
		assertEquals(TipoEvento.EXECUCAO_FALHOU, evento.tipo());
		assertEquals("suspensao comprometida", evento.dados().get("motivo"));
	}

	@Test
	@DisplayName("OS sem execucao: nao encontrada")
	void naoEncontrada() {
		assertThrows(ExecucaoNaoEncontradaException.class, () -> execucaoService.buscarPorOrdemServico(999L));
	}

	@Test
	@DisplayName("a fila vem do repositorio, filtrada pelos status de espera")
	void listarFila() {
		when(execucaoRepository.findByStatusInOrderByCriadaEmAsc(
				List.of(StatusExecucao.NA_FILA, StatusExecucao.AGUARDANDO_ITENS)))
				.thenReturn(List.of(execucaoNaFila()));

		assertEquals(1, execucaoService.listarFila().size());
	}
}
