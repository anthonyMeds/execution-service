package br.com.oficina.execution.messaging;

import java.util.List;

/**
 * Cabecalhos do trace distribuido (W3C e New Relic).
 *
 * Quando um comando chega com esses cabecalhos e este servico publica um evento
 * em resposta, os mesmos valores vao no evento. Assim o New Relic mostra um trace
 * so, do OS Service, passando por aqui, e voltando ao OS Service. Sem isso, o
 * trace terminaria neste servico.
 */
public final class Rastreamento {

	public static final List<String> CABECALHOS = List.of("traceparent", "tracestate", "newrelic");

	private Rastreamento() {
	}
}
