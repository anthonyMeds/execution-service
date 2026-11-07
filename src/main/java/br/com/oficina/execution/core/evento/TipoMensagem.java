package br.com.oficina.execution.core.evento;

/**
 * Os tipos que este servico publica. Os nomes sao exatamente os do documento
 * de contratos do time -- evento e verbo no passado, comando e imperativo.
 */
public final class TipoMensagem {

	public static final String EXECUCAO_AGUARDANDO_ITENS = "ExecucaoAguardandoItens";
	public static final String EXECUCAO_INICIADA = "ExecucaoIniciada";
	public static final String SERVICO_FINALIZADO = "ServicoFinalizado";
	public static final String EXECUCAO_FINALIZADA = "ExecucaoFinalizada";
	public static final String EXECUCAO_FALHOU = "ExecucaoFalhou";

	private TipoMensagem() {
	}
}
