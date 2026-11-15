package br.com.oficina.execution.messaging;

import java.time.Instant;
import java.util.Map;

import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.oficina.execution.config.CorrelationIdFilter;
import br.com.oficina.execution.model.Execucao;

/**
 * O formato de toda mensagem trocada entre os tres servicos, seja comando
 * (chega) ou evento (sai).
 *
 * "Envelope" porque a parte de fora e sempre igual -- de quem e a saga, qual
 * OS, que tipo de mensagem -- e so o conteudo de "dados" muda conforme o tipo.
 *
 * Campos desconhecidos sao ignorados na leitura: se outro servico acrescentar
 * um campo amanha, este nao quebra.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope(
		String sagaId,
		String tipo,
		Long ordemServicoId,
		String codigoOrdemServico,
		String correlationId,
		Instant ocorridoEm,
		Map<String, Object> dados
) {

	/** Monta o envelope de um evento sobre esta execucao. */
	public static Envelope de(Execucao execucao, String tipo, Map<String, Object> dados) {
		return new Envelope(
				execucao.getSagaId(),
				tipo,
				execucao.getOrdemServicoId(),
				execucao.getCodigoOrdemServico(),
				MDC.get(CorrelationIdFilter.MDC_KEY),
				Instant.now(),
				dados);
	}
}
