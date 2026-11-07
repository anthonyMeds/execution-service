package br.com.oficina.execution.core.usecase;

import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.MovimentacaoEstoque;
import br.com.oficina.execution.core.domain.PecaRequisitada;
import br.com.oficina.execution.core.domain.ServicoExecucao;
import br.com.oficina.execution.core.domain.TipoMovimentacao;
import br.com.oficina.execution.core.evento.EnvelopeEvento;
import br.com.oficina.execution.core.evento.TipoMensagem;
import br.com.oficina.execution.core.port.out.EventoPublisherPort;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;
import br.com.oficina.execution.core.port.out.MovimentacaoEstoqueRepositoryPort;

/**
 * Fecha a execucao. Aqui a reserva vira baixa definitiva, e o evento publicado
 * leva a OS para FINALIZADA, pronta para entrega.
 */
@Service
public class FinalizarExecucaoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final MovimentacaoEstoqueRepositoryPort movimentacoes;
	private final EventoPublisherPort eventos;
	private final BuscadorDeExecucao buscador;

	public FinalizarExecucaoUseCase(
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

	public Execucao executar(Long ordemServicoId) {
		Execucao execucao = buscador.porOrdemServico(ordemServicoId);
		execucao.finalizar();

		for (PecaRequisitada peca : execucao.getPecas()) {
			if (!movimentacoes.jaRegistrada(execucao.getSagaId(), peca.itemId(), TipoMovimentacao.BAIXA)) {
				movimentacoes.registrar(MovimentacaoEstoque.nova(
						execucao.getSagaId(), peca.itemId(), peca.quantidade(), TipoMovimentacao.BAIXA));
			}
		}

		Execucao salva = execucoes.salvar(execucao);

		eventos.publicar(EnvelopeEvento.de(salva, TipoMensagem.EXECUCAO_FINALIZADA, Map.of(
				"execucaoId", String.valueOf(salva.getId()),
				"duracaoTotalMinutos", salva.duracaoMinutos(),
				"servicosExecutados", salva.getServicos().stream()
						.map(ServicoExecucao::getServicoId)
						.toList())));

		return salva;
	}
}
