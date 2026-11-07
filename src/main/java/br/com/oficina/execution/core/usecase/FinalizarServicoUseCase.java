package br.com.oficina.execution.core.usecase;

import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.ServicoExecucao;
import br.com.oficina.execution.core.evento.EnvelopeEvento;
import br.com.oficina.execution.core.evento.TipoMensagem;
import br.com.oficina.execution.core.port.out.EventoPublisherPort;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;

/** O mecanico concluiu um servico da OS. */
@Service
public class FinalizarServicoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final EventoPublisherPort eventos;
	private final BuscadorDeExecucao buscador;

	public FinalizarServicoUseCase(
			ExecucaoRepositoryPort execucoes,
			EventoPublisherPort eventos,
			BuscadorDeExecucao buscador
	) {
		this.execucoes = execucoes;
		this.eventos = eventos;
		this.buscador = buscador;
	}

	public Execucao executar(Long ordemServicoId, Long servicoId, String observacao) {
		Execucao execucao = buscador.porOrdemServico(ordemServicoId);
		execucao.finalizarServico(servicoId, observacao);
		Execucao salva = execucoes.salvar(execucao);

		long duracao = salva.getServicos().stream()
				.filter(s -> s.getServicoId().equals(servicoId))
				.mapToLong(ServicoExecucao::duracaoMinutos)
				.findFirst()
				.orElse(0L);

		eventos.publicar(EnvelopeEvento.de(salva, TipoMensagem.SERVICO_FINALIZADO, Map.of(
				"execucaoId", String.valueOf(salva.getId()),
				"servicoId", servicoId,
				"duracaoMinutos", duracao)));

		return salva;
	}
}
