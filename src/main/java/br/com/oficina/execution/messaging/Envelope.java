package br.com.oficina.execution.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
		String messageId,
		String tipo,
		String sagaId,
		Long ordemServicoId,
		String codigoOrdemServico,
		String correlationId,
		Instant ocorridoEm,
		Integer versao,
		Map<String, Object> dados
) {

	/** Versao do formato do envelope, igual nos tres servicos. */
	public static final int VERSAO_ATUAL = 1;

	/**
	 * Monta o envelope de um evento sobre esta execucao.
	 *
	 * O sagaId e devolvido exatamente como chegou no comando: e por ele que o OS
	 * Service sabe a qual ciclo da OS o evento pertence. O messageId e novo a cada
	 * evento; o OS Service usa para descartar a mesma mensagem entregue duas vezes.
	 */
	public static Envelope de(Execucao execucao, String tipo, Map<String, Object> dados) {
		return new Envelope(
				UUID.randomUUID().toString(),
				tipo,
				execucao.getSagaId(),
				execucao.getOrdemServicoId(),
				execucao.getCodigoOrdemServico(),
				MDC.get(CorrelationIdFilter.MDC_KEY),
				Instant.now(),
				VERSAO_ATUAL,
				dados);
	}
}
