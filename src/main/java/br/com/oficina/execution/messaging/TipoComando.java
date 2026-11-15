package br.com.oficina.execution.messaging;

/** Comandos que este servico aceita. Os nomes sao os do contrato do time. */
public final class TipoComando {

	public static final String ENFILEIRAR_EXECUCAO = "EnfileirarExecucao";
	public static final String CANCELAR_EXECUCAO = "CancelarExecucao";

	private TipoComando() {
	}
}
