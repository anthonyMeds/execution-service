package br.com.oficina.execution.model;

import java.time.Instant;

/** Uma linha da linha do tempo da execucao. So se acrescenta, nunca se edita. */
public record EventoHistorico(String evento, Instant em, String detalhe) {

	public static EventoHistorico agora(String evento, String detalhe) {
		return new EventoHistorico(evento, Instant.now(), detalhe);
	}
}
