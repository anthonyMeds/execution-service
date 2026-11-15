package br.com.oficina.execution.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.dto.TempoMedioResponse;
import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.model.StatusExecucao;
import br.com.oficina.execution.repository.ExecucaoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Tempo medio de execucao. Era o pacote relatorio/ do monolito da Fase 3, e
 * veio para ca porque quem grava a hora de inicio e de fim e este servico.
 */
@Service
@RequiredArgsConstructor
public class RelatorioService {

	private final ExecucaoRepository execucaoRepository;

	/** Quanto tempo, em media, uma OS passa em execucao. */
	public TempoMedioResponse tempoMedioDeOrdemServico() {
		List<Long> duracoes = execucaoRepository.findByStatus(StatusExecucao.FINALIZADA).stream()
				.map(Execucao::duracaoSegundos)
				.toList();
		return TempoMedioResponse.de("ORDEM_SERVICO", "Ordem de servico", duracoes);
	}

	/** Quanto tempo, em media, leva cada tipo de servico. */
	public List<TempoMedioResponse> tempoMedioPorServico() {
		Map<Long, String> nomes = new LinkedHashMap<>();
		Map<Long, List<Long>> duracoesPorServico = new LinkedHashMap<>();

		for (Execucao execucao : execucaoRepository.findByStatus(StatusExecucao.FINALIZADA)) {
			for (ServicoExecucao servico : execucao.getServicos()) {
				if (servico.concluido()) {
					nomes.putIfAbsent(servico.getServicoId(), servico.getNome());
					duracoesPorServico
							.computeIfAbsent(servico.getServicoId(), id -> new ArrayList<>())
							.add(servico.duracaoSegundos());
				}
			}
		}

		List<TempoMedioResponse> relatorio = new ArrayList<>();
		duracoesPorServico.forEach((servicoId, duracoes) ->
				relatorio.add(TempoMedioResponse.de(String.valueOf(servicoId), nomes.get(servicoId), duracoes)));
		return relatorio;
	}
}
