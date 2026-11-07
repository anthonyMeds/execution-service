package br.com.oficina.execution.core.domain;

import java.time.Instant;

/**
 * Linha do tempo da execucao, append-only. Substitui o historico_ordem_servico
 * da Fase 3 no que diz respeito a execucao.
 */
public record EventoHistorico(String evento, Instant em, String detalhe) {

	public static EventoHistorico de(String evento, String detalhe) {
		return new EventoHistorico(evento, Instant.now(), detalhe);
	}
}
