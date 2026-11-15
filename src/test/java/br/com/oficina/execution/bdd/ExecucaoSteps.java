package br.com.oficina.execution.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import br.com.oficina.execution.exception.TransicaoInvalidaException;
import br.com.oficina.execution.messaging.Envelope;
import br.com.oficina.execution.messaging.EventoPublisher;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.service.EstoqueService;
import br.com.oficina.execution.service.ExecucaoService;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;

/**
 * Cada metodo daqui e uma frase do arquivo execucao.feature.
 *
 * Os services sao os de verdade; so o banco e a fila sao de mentira (em
 * memoria). Assim o cenario exercita a regra real, do comando ate o evento.
 */
public class ExecucaoSteps {

	private EstoqueService estoqueService;
	private ExecucaoService execucaoService;
	private final List<Envelope> eventosPublicados = new ArrayList<>();

	/** Antes de cada cenario: banco vazio e nenhum evento publicado. */
	@Before
	public void comecarDoZero() {
		eventosPublicados.clear();
		EventoPublisher publicadorQueSoGuarda = eventosPublicados::add;
		estoqueService = new EstoqueService(BancoEmMemoria.movimentacaoRepository());
		execucaoService = new ExecucaoService(
				BancoEmMemoria.execucaoRepository(), estoqueService, publicadorQueSoGuarda);
	}

	@Dado("que existem {int} unidades da peca {long} em estoque")
	public void queExistemUnidadesEmEstoque(int quantidade, Long itemId) {
		estoqueService.registrarEntrada("NF-INICIAL", itemId, quantidade);
	}

	@Quando("chegam mais {int} unidades da peca {long} pela nota {string}")
	public void chegamMaisUnidades(int quantidade, Long itemId, String notaFiscal) {
		estoqueService.registrarEntrada(notaFiscal, itemId, quantidade);
	}

	@Quando("chega o comando para enfileirar a OS {long} com os servicos {long} e {long} e {int} unidades da peca {long}")
	public void chegaOComandoParaEnfileirar(Long ordemServicoId, Long servico1, Long servico2, int quantidade, Long itemId) {
		execucaoService.enfileirar(
				"saga-" + ordemServicoId,
				ordemServicoId,
				"OS-2026-" + ordemServicoId,
				List.of(new ServicoExecucao(servico1, "Servico " + servico1),
						new ServicoExecucao(servico2, "Servico " + servico2)),
				List.of(new PecaRequisitada(itemId, quantidade)));
	}

	@Quando("o mecanico {string} inicia a execucao da OS {long}")
	public void oMecanicoInicia(String mecanico, Long ordemServicoId) {
		execucaoService.iniciar(ordemServicoId, mecanico);
	}

	@Entao("o mecanico {string} nao consegue iniciar a execucao da OS {long}")
	public void oMecanicoNaoConsegueIniciar(String mecanico, Long ordemServicoId) {
		assertThrows(TransicaoInvalidaException.class, () -> execucaoService.iniciar(ordemServicoId, mecanico));
	}

	@Quando("o mecanico finaliza o servico {long} da OS {long}")
	public void oMecanicoFinalizaOServico(Long servicoId, Long ordemServicoId) {
		execucaoService.finalizarServico(ordemServicoId, servicoId, "concluido");
	}

	@Quando("a execucao da OS {long} e finalizada")
	public void aExecucaoEFinalizada(Long ordemServicoId) {
		execucaoService.finalizar(ordemServicoId);
	}

	@Quando("a execucao da OS {long} falha por {string}")
	public void aExecucaoFalha(Long ordemServicoId, String motivo) {
		execucaoService.falhar(ordemServicoId, motivo, "EXECUCAO_SERVICO");
	}

	@Entao("a execucao da OS {long} fica com status {string}")
	public void aExecucaoFicaComStatus(Long ordemServicoId, String status) {
		assertEquals(status, execucaoService.buscarPorOrdemServico(ordemServicoId).getStatus().name());
	}

	@Entao("o saldo da peca {long} passa a ser {int}")
	public void oSaldoDaPecaPassaASer(Long itemId, int esperado) {
		assertEquals(esperado, estoqueService.saldoDe(itemId));
	}

	@Entao("o evento {string} e publicado")
	public void oEventoEPublicado(String tipo) {
		assertTrue(eventosPublicados.stream().anyMatch(e -> e.tipo().equals(tipo)),
				"o evento " + tipo + " nao foi publicado");
	}

	@Entao("a fila da oficina tem {int} execucao")
	public void aFilaDaOficinaTem(int quantidade) {
		assertEquals(quantidade, execucaoService.listarFila().size());
	}
}
