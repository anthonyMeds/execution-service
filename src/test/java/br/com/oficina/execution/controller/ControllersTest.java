package br.com.oficina.execution.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import br.com.oficina.execution.dto.EntradaEstoqueRequest;
import br.com.oficina.execution.dto.ErroResponse;
import br.com.oficina.execution.dto.ExecucaoResponse;
import br.com.oficina.execution.dto.FalharExecucaoRequest;
import br.com.oficina.execution.dto.FinalizarServicoRequest;
import br.com.oficina.execution.dto.IniciarExecucaoRequest;
import br.com.oficina.execution.dto.TempoMedioResponse;
import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.exception.TransicaoInvalidaException;
import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.service.EstoqueService;
import br.com.oficina.execution.service.ExecucaoService;
import br.com.oficina.execution.service.RelatorioService;

/**
 * Os controllers sao finos: recebem, chamam o service e convertem a resposta.
 * Aqui eles sao chamados direto, como classes Java comuns, com o service de
 * mentira -- o que se confere e a conversao para o formato de resposta.
 */
@ExtendWith(MockitoExtension.class)
class ControllersTest {

	private static final Long OS = 1042L;

	@Mock
	private ExecucaoService execucaoService;

	@Mock
	private EstoqueService estoqueService;

	@Mock
	private RelatorioService relatorioService;

	private Execucao execucao() {
		return new Execucao("saga-1", OS, "OS-2026-000123",
				List.of(new ServicoExecucao(7L, "Troca de pastilhas")),
				List.of(new PecaRequisitada(55L, 4)));
	}

	@Test
	@DisplayName("fila e busca devolvem a execucao no formato de resposta")
	void consultas() {
		ExecucaoController controller = new ExecucaoController(execucaoService);
		when(execucaoService.listarFila()).thenReturn(List.of(execucao()));
		when(execucaoService.buscarPorOrdemServico(OS)).thenReturn(execucao());

		List<ExecucaoResponse> fila = controller.fila();
		ExecucaoResponse resposta = controller.buscar(OS);

		assertEquals(1, fila.size());
		assertEquals("NA_FILA", resposta.status());
		assertEquals("OS-2026-000123", resposta.codigoOrdemServico());
		assertEquals("Troca de pastilhas", resposta.servicos().get(0).nome());
		assertEquals("PENDENTE", resposta.servicos().get(0).status());
	}

	@Test
	@DisplayName("iniciar, finalizar servico, finalizar e falhar repassam para o service")
	void acoes() {
		ExecucaoController controller = new ExecucaoController(execucaoService);
		Execucao emExecucao = execucao();
		emExecucao.iniciar("Carlos");
		Execucao finalizada = execucao();
		finalizada.iniciar("Carlos");
		finalizada.finalizarServico(7L, "ok");
		finalizada.finalizar();
		Execucao falhou = execucao();
		falhou.falhar("peca com defeito");

		when(execucaoService.iniciar(OS, "Carlos")).thenReturn(emExecucao);
		when(execucaoService.finalizarServico(OS, 7L, "ok")).thenReturn(emExecucao);
		when(execucaoService.finalizar(OS)).thenReturn(finalizada);
		when(execucaoService.falhar(OS, "peca com defeito", "EXECUCAO_SERVICO")).thenReturn(falhou);

		assertEquals("EM_EXECUCAO", controller.iniciar(OS, new IniciarExecucaoRequest("Carlos")).status());
		assertEquals("Carlos", controller.finalizarServico(OS, 7L, new FinalizarServicoRequest("ok")).mecanicoResponsavel());
		assertEquals("FINALIZADA", controller.finalizar(OS).status());
		ExecucaoResponse resposta = controller.falhar(OS, new FalharExecucaoRequest("peca com defeito", "EXECUCAO_SERVICO"));
		assertEquals("FALHOU", resposta.status());
		assertEquals("peca com defeito", resposta.motivoEncerramento());
	}

	@Test
	@DisplayName("estoque: saldo e entrada devolvem o saldo do item")
	void estoque() {
		EstoqueController controller = new EstoqueController(estoqueService);
		when(estoqueService.saldoDe(55L)).thenReturn(6);
		when(estoqueService.registrarEntrada("NF-900", 55L, 10)).thenReturn(16);

		assertEquals(6, controller.saldo(55L).saldo());
		assertEquals(16, controller.registrarEntrada(55L, new EntradaEstoqueRequest("NF-900", 10)).saldo());
		assertEquals(55L, controller.saldo(55L).itemId());
	}

	@Test
	@DisplayName("relatorios repassam o que o service calculou")
	void relatorios() {
		RelatorioController controller = new RelatorioController(relatorioService);
		TempoMedioResponse media = new TempoMedioResponse("ORDEM_SERVICO", "Ordem de servico", 90.0, 2);
		when(relatorioService.tempoMedioDeOrdemServico()).thenReturn(media);
		when(relatorioService.tempoMedioPorServico()).thenReturn(List.of(media));

		assertEquals(90.0, controller.tempoMedioDeOrdemServico().mediaMinutos());
		assertEquals(1, controller.tempoMedioPorServico().size());
	}

	@Test
	@DisplayName("cada excecao de negocio vira o status HTTP certo")
	void tratadorDeErros() {
		TratadorDeErros tratador = new TratadorDeErros();

		ResponseEntity<ErroResponse> naoEncontrada = tratador.naoEncontrada(new ExecucaoNaoEncontradaException(999L));
		ResponseEntity<ErroResponse> conflito = tratador.transicaoInvalida(new TransicaoInvalidaException("ja iniciou"));
		ResponseEntity<ErroResponse> invalida = tratador.argumentoInvalido(new IllegalArgumentException("quantidade"));

		assertEquals(404, naoEncontrada.getStatusCode().value());
		assertEquals("EXECUCAO_NAO_ENCONTRADA", naoEncontrada.getBody().erro());
		assertEquals(409, conflito.getStatusCode().value());
		assertEquals("ja iniciou", conflito.getBody().mensagem());
		assertEquals(400, invalida.getStatusCode().value());
	}
}
