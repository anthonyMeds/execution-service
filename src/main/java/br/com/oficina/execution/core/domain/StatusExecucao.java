package br.com.oficina.execution.core.domain;

import java.util.Set;

/**
 * Estados da execucao. Nao confundir com StatusOrdemServico, que vive no OS Service
 * e e o unico status que o cliente enxerga: aqui o estado e interno da oficina, e
 * cada transicao daqui publica um evento que faz a OS transicionar la.
 */
public enum StatusExecucao {

	NA_FILA,
	AGUARDANDO_ITENS,
	EM_EXECUCAO,
	FINALIZADA,
	CANCELADA,
	FALHOU;

	private static final Set<StatusExecucao> ENCERRADOS = Set.of(FINALIZADA, CANCELADA, FALHOU);

	public boolean encerrado() {
		return ENCERRADOS.contains(this);
	}

	public boolean aguardando() {
		return this == NA_FILA || this == AGUARDANDO_ITENS;
	}
}
