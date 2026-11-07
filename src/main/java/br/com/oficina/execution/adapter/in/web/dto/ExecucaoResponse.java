package br.com.oficina.execution.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.ServicoExecucao;

public record ExecucaoResponse(
		String id,
		Long ordemServicoId,
		String codigoOrdemServico,
		String status,
		String mecanicoResponsavel,
		List<ServicoResponse> servicos,
		Instant criadaEm,
		Instant iniciadaEm,
		Instant finalizadaEm,
		long duracaoMinutos,
		String motivoEncerramento
) {

	public static ExecucaoResponse de(Execucao execucao) {
		return new ExecucaoResponse(
				execucao.getId(),
				execucao.getOrdemServicoId(),
				execucao.getCodigoOrdemServico(),
				execucao.getStatus().name(),
				execucao.getMecanicoResponsavel().orElse(null),
				execucao.getServicos().stream().map(ServicoResponse::de).toList(),
				execucao.getCriadaEm(),
				execucao.getIniciadaEm(),
				execucao.getFinalizadaEm(),
				execucao.duracaoMinutos(),
				execucao.getMotivoEncerramento());
	}

	public record ServicoResponse(
			Long servicoId,
			String nome,
			String status,
			Instant iniciadoEm,
			Instant finalizadoEm,
			long duracaoMinutos,
			String observacao
	) {

		static ServicoResponse de(ServicoExecucao servico) {
			return new ServicoResponse(
					servico.getServicoId(),
					servico.getNome(),
					servico.getStatus().name(),
					servico.getIniciadoEm(),
					servico.getFinalizadoEm(),
					servico.duracaoMinutos(),
					servico.getObservacao());
		}
	}
}
