package br.com.oficina.execution.model;

/**
 * Estados da execucao dentro da oficina.
 *
 * Nao confundir com o status da ordem de servico, que vive no OS Service e e o
 * que o cliente ve. Cada mudanca aqui vira um evento, e e o OS Service que
 * decide o que fazer com a OS.
 */
public enum StatusExecucao {

	NA_FILA,
	AGUARDANDO_ITENS,
	EM_EXECUCAO,
	FINALIZADA,
	CANCELADA,
	FALHOU;

	/** Ainda nao comecou: esta esperando mecanico ou peca. */
	public boolean aguardando() {
		return this == NA_FILA || this == AGUARDANDO_ITENS;
	}

	/** Estado final: depois dele nada mais acontece com a execucao. */
	public boolean encerrado() {
		return this == FINALIZADA || this == CANCELADA || this == FALHOU;
	}
}
