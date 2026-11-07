package br.com.oficina.execution.core.usecase;

import java.util.ArrayList;
import java.util.List;
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
 * Entrada da saga neste servico: reage ao comando EnfileirarExecucao, que o OS
 * Service publica depois que o Billing confirmou o pagamento.
 */
@Service
public class EnfileirarExecucaoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final MovimentacaoEstoqueRepositoryPort movimentacoes;
	private final EventoPublisherPort eventos;

	public EnfileirarExecucaoUseCase(
			ExecucaoRepositoryPort execucoes,
			MovimentacaoEstoqueRepositoryPort movimentacoes,
			EventoPublisherPort eventos
	) {
		this.execucoes = execucoes;
		this.movimentacoes = movimentacoes;
		this.eventos = eventos;
	}

	public Execucao executar(ComandoEnfileirarExecucao comando) {
		// Idempotencia. SQS entrega at-least-once, entao este mesmo comando pode
		// chegar de novo; a segunda vez devolve o que ja existe em vez de criar
		// uma segunda execucao da mesma OS.
		var jaExiste = execucoes.porSagaId(comando.sagaId());
		if (jaExiste.isPresent()) {
			return jaExiste.get();
		}

		Execucao execucao = Execucao.enfileirar(
				comando.sagaId(),
				comando.ordemServicoId(),
				comando.codigoOrdemServico(),
				comando.servicos().stream()
						.map(s -> new ServicoExecucao(s.servicoId(), s.nome()))
						.toList(),
				comando.itens().stream()
						.map(i -> new PecaRequisitada(i.itemId(), i.quantidade()))
						.toList()
		);

		List<Long> emFalta = reservarPecas(execucao);
		if (!emFalta.isEmpty()) {
			execucao.aguardarItens(emFalta);
		}

		Execucao salva = execucoes.salvar(execucao);

		if (!emFalta.isEmpty()) {
			eventos.publicar(EnvelopeEvento.de(
					salva,
					TipoMensagem.EXECUCAO_AGUARDANDO_ITENS,
					Map.of("itensEmFalta", emFalta)));
		}
		return salva;
	}

	/**
	 * Reserva o que da e devolve o que faltou. Nao lanca excecao por falta de peca:
	 * falta de peca nao e erro, e o estado AGUARDANDO_ITENS, que ja existe no
	 * StatusOrdemServico da Fase 3.
	 */
	private List<Long> reservarPecas(Execucao execucao) {
		List<Long> emFalta = new ArrayList<>();
		for (PecaRequisitada peca : execucao.getPecas()) {
			if (movimentacoes.jaRegistrada(execucao.getSagaId(), peca.itemId(), TipoMovimentacao.RESERVA)) {
				continue;
			}
			if (movimentacoes.saldoDe(peca.itemId()) < peca.quantidade()) {
				emFalta.add(peca.itemId());
				continue;
			}
			movimentacoes.registrar(MovimentacaoEstoque.nova(
					execucao.getSagaId(), peca.itemId(), peca.quantidade(), TipoMovimentacao.RESERVA));
		}
		return emFalta;
	}
}
