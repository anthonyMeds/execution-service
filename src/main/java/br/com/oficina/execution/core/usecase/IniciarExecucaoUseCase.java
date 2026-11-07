package br.com.oficina.execution.core.usecase;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.evento.EnvelopeEvento;
import br.com.oficina.execution.core.evento.TipoMensagem;
import br.com.oficina.execution.core.port.out.EventoPublisherPort;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;

/** O mecanico pega a OS da fila. Faz a OS ir para EM_EXECUCAO la no OS Service. */
@Service
public class IniciarExecucaoUseCase {

	private final ExecucaoRepositoryPort execucoes;
	private final EventoPublisherPort eventos;
	private final BuscadorDeExecucao buscador;

	public IniciarExecucaoUseCase(
			ExecucaoRepositoryPort execucoes,
			EventoPublisherPort eventos,
			BuscadorDeExecucao buscador
	) {
		this.execucoes = execucoes;
		this.eventos = eventos;
		this.buscador = buscador;
	}

	public Execucao executar(Long ordemServicoId, String mecanico) {
		Execucao execucao = buscador.porOrdemServico(ordemServicoId);
		execucao.iniciar(mecanico);
		Execucao salva = execucoes.salvar(execucao);

		Map<String, Object> dados = new HashMap<>();
		dados.put("execucaoId", salva.getId());
		dados.put("mecanico", mecanico);
		dados.put("iniciadoEm", salva.getIniciadaEm());
		eventos.publicar(EnvelopeEvento.de(salva, TipoMensagem.EXECUCAO_INICIADA, dados));

		return salva;
	}
}
