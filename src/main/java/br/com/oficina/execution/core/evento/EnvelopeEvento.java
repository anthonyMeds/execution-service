package br.com.oficina.execution.core.evento;

import java.time.Instant;
import java.util.Map;

import br.com.oficina.execution.core.domain.Execucao;

/**
 * Envelope comum aos tres servicos, igual ao documento de contratos.
 * O correlationId e o mesmo do header x-correlation-id, para o rastro nao se
 * perder justamente na travessia entre servicos.
 */
public record EnvelopeEvento(
		String sagaId,
		String tipo,
		Long ordemServicoId,
		String codigoOrdemServico,
		String correlationId,
		Instant ocorridoEm,
		Map<String, Object> dados
) {

	public static EnvelopeEvento de(Execucao execucao, String tipo, Map<String, Object> dados) {
		return new EnvelopeEvento(
				execucao.getSagaId(),
				tipo,
				execucao.getOrdemServicoId(),
				execucao.getCodigoOrdemServico(),
				org.slf4j.MDC.get("correlationId"),
				Instant.now(),
				dados == null ? Map.of() : dados
		);
	}
}
