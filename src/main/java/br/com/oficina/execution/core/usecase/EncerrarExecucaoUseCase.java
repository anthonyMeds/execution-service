package br.com.oficina.execution.core.usecase;

import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.MovimentacaoEstoque;
import br.com.oficina.execution.core.domain.PecaRequisitada;
import br.com.oficina.execution.core.domain.TipoMovimentacao;
import br.com.oficina.execution.core.evento.EnvelopeEvento;
import br.com.oficina.execution.core.evento.TipoMensagem;
import br.com.oficina.execution.core.port.out.EventoPublisherPort;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;
import br.com.oficina.execution.core.port.out.MovimentacaoEstoqueRepositoryPort;

/**
 * Os dois caminhos de encerramento sem sucesso, que devolvem o estoque reservado:
 *
 * cancelar -- o OS Service mandou CancelarExecucao (compensacao vinda de fora).
 *             Nao publica evento de falha: quem cancelou ja sabe.
 * falhar   -- a execucao quebrou aqui dentro. Publica ExecucaoFalhou, que e o
 *             gatilho do estorno no Mercado Pago la no Billing. E a compensacao
 *             mais importante da saga.
 */
@Service
public class EncerrarExecucaoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final MovimentacaoEstoqueRepositoryPort movimentacoes;
	private final EventoPublisherPort eventos;
	private final BuscadorDeExecucao buscador;

	public EncerrarExecucaoUseCase(
			ExecucaoRepositoryPort execucoes,
			MovimentacaoEstoqueRepositoryPort movimentacoes,
			EventoPublisherPort eventos,
			BuscadorDeExecucao buscador
	) {
		this.execucoes = execucoes;
		this.movimentacoes = movimentacoes;
		this.eventos = eventos;
		this.buscador = buscador;
	}

	public Execucao cancelar(Long ordemServicoId, String motivo) {
		Execucao execucao = buscador.porOrdemServico(ordemServicoId);
		execucao.cancelar(motivo);
		devolverEstoque(execucao);
		return execucoes.salvar(execucao);
	}

	public Execucao falhar(Long ordemServicoId, String motivo, String etapa) {
		Execucao execucao = buscador.porOrdemServico(ordemServicoId);
		execucao.falhar(motivo);
		devolverEstoque(execucao);
		Execucao salva = execucoes.salvar(execucao);

		eventos.publicar(EnvelopeEvento.de(salva, TipoMensagem.EXECUCAO_FALHOU, Map.of(
				"motivo", motivo,
				"etapa", etapa)));

		return salva;
	}

	private void devolverEstoque(Execucao execucao) {
		for (PecaRequisitada peca : execucao.getPecas()) {
			if (!movimentacoes.jaRegistrada(execucao.getSagaId(), peca.itemId(), TipoMovimentacao.ESTORNO)) {
				movimentacoes.registrar(MovimentacaoEstoque.nova(
						execucao.getSagaId(), peca.itemId(), peca.quantidade(), TipoMovimentacao.ESTORNO));
			}
		}
	}
}
