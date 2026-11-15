package br.com.oficina.execution.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.service.ExecucaoService;
import tools.jackson.databind.json.JsonMapper;

/** Le JSON de verdade (com o Jackson) e confere o que chega no service. */
@ExtendWith(MockitoExtension.class)
class ComandoHandlerTest {

	@Mock
	private ExecucaoService execucaoService;

	@Captor
	private ArgumentCaptor<List<ServicoExecucao>> servicosCaptor;

	@Captor
	private ArgumentCaptor<List<PecaRequisitada>> pecasCaptor;

	private ComandoHandler handler;

	@BeforeEach
	void preparar() {
		handler = new ComandoHandler(execucaoService, JsonMapper.builder().build());
	}

	@Test
	@DisplayName("EnfileirarExecucao: servicos e itens do JSON chegam no service")
	void enfileirar() {
		handler.tratar("""
				{
				  "sagaId": "saga-1",
				  "tipo": "EnfileirarExecucao",
				  "ordemServicoId": 1042,
				  "codigoOrdemServico": "OS-2026-000123",
				  "correlationId": "corr-1",
				  "ocorridoEm": "2026-11-07T14:35:02Z",
				  "campoQueEsteServicoNaoConhece": true,
				  "dados": {
				    "servicos": [ { "servicoId": 7, "nome": "Troca de pastilhas" } ],
				    "itens": [ { "itemId": 55, "quantidade": 4 } ]
				  }
				}
				""");

		verify(execucaoService).enfileirar(
				eq("saga-1"), eq(1042L), eq("OS-2026-000123"), servicosCaptor.capture(), pecasCaptor.capture());
		assertEquals(1, servicosCaptor.getValue().size());
		assertEquals(7L, servicosCaptor.getValue().get(0).getServicoId());
		assertEquals("Troca de pastilhas", servicosCaptor.getValue().get(0).getNome());
		assertEquals(List.of(new PecaRequisitada(55L, 4)), pecasCaptor.getValue());
	}

	@Test
	@DisplayName("aceita o envelope exatamente como o OS Service publica (messageId, versao, sagaId em UUID)")
	void envelopeDoOsService() {
		// Copiado do formato de EnvelopeMensagem do oficina-os-service
		handler.tratar("""
				{
				  "messageId": "6f1c2b9e-3d47-4a8e-9b1f-2c5d7e8a9b01",
				  "tipo": "EnfileirarExecucao",
				  "sagaId": "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
				  "ordemServicoId": 1042,
				  "codigoOrdemServico": "OS-2026-000123",
				  "correlationId": "8c21f4b0-11de-4a77-9b03-5c2e7a19d4f6",
				  "ocorridoEm": "2026-11-15T14:35:02.123456Z",
				  "versao": 1,
				  "dados": {
				    "servicos": [ { "servicoId": 7, "nome": "Troca de pastilhas" } ],
				    "itens": [ { "itemId": 55, "quantidade": 4 } ]
				  }
				}
				""");

		verify(execucaoService).enfileirar(
				eq("3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"), eq(1042L), eq("OS-2026-000123"),
				servicosCaptor.capture(), pecasCaptor.capture());
		assertEquals("Troca de pastilhas", servicosCaptor.getValue().get(0).getNome());
	}

	@Test
	@DisplayName("EnfileirarExecucao sem itens: lista vazia, nao erro")
	void enfileirarSemItens() {
		handler.tratar("""
				{ "sagaId": "saga-2", "tipo": "EnfileirarExecucao", "ordemServicoId": 2000,
				  "codigoOrdemServico": "OS-2", "dados": { "servicos": [ { "servicoId": 3, "nome": "Alinhamento" } ] } }
				""");

		verify(execucaoService).enfileirar(
				eq("saga-2"), eq(2000L), eq("OS-2"), servicosCaptor.capture(), pecasCaptor.capture());
		assertTrue(pecasCaptor.getValue().isEmpty());
	}

	@Test
	@DisplayName("CancelarExecucao: chama o cancelamento com o motivo que veio")
	void cancelar() {
		handler.tratar("""
				{ "sagaId": "saga-1", "tipo": "CancelarExecucao", "ordemServicoId": 1042,
				  "dados": { "motivo": "cliente desistiu" } }
				""");

		verify(execucaoService).cancelar(1042L, "cliente desistiu");
	}

	@Test
	@DisplayName("CancelarExecucao sem motivo: usa um texto padrao")
	void cancelarSemMotivo() {
		handler.tratar("""
				{ "sagaId": "saga-1", "tipo": "CancelarExecucao", "ordemServicoId": 1042 }
				""");

		verify(execucaoService).cancelar(1042L, "cancelado pelo OS Service");
	}

	@Test
	@DisplayName("cancelar uma OS que nunca chegou aqui nao e erro")
	void cancelarInexistente() {
		when(execucaoService.cancelar(999L, "cancelado pelo OS Service"))
				.thenThrow(new ExecucaoNaoEncontradaException(999L));

		assertDoesNotThrow(() -> handler.tratar("""
				{ "sagaId": "saga-9", "tipo": "CancelarExecucao", "ordemServicoId": 999 }
				"""));
	}

	@Test
	@DisplayName("comando desconhecido e ignorado")
	void comandoDesconhecido() {
		handler.tratar("""
				{ "sagaId": "saga-1", "tipo": "ComandoQueNaoExiste", "ordemServicoId": 1 }
				""");

		verifyNoInteractions(execucaoService);
	}

	@Test
	@DisplayName("mensagem que nao e JSON estoura, para ir parar na fila de mensagens com defeito")
	void jsonInvalido() {
		assertThrows(RuntimeException.class, () -> handler.tratar("isto nao e json"));
	}

	@Test
	@DisplayName("o publicador de log aceita qualquer evento sem precisar de broker")
	void publicadorDeLog() {
		Envelope envelope = new Envelope(
				"msg-1", TipoEvento.EXECUCAO_INICIADA, "saga-1", 1042L, "OS-1", null, null, Envelope.VERSAO_ATUAL, Map.of());

		assertDoesNotThrow(() -> new LogEventoPublisher().publicar(envelope));
	}
}
