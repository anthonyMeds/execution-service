package br.com.oficina.execution.core.usecase;

import java.util.List;

/**
 * O comando EnfileirarExecucao como ele chega no envelope, ja desembrulhado.
 *
 * Repare que o servico vem com nome, e nao so com id: e duplicacao de proposito.
 * O Execution precisa mostrar "Troca de pastilhas de freio" na tela do mecanico e
 * nao pode consultar o catalogo do Billing para isso -- preferir uma copia barata
 * a um acoplamento caro e o que mantem os servicos independentes.
 */
public record ComandoEnfileirarExecucao(
		String sagaId,
		Long ordemServicoId,
		String codigoOrdemServico,
		List<ServicoSolicitado> servicos,
		List<PecaSolicitada> itens
) {

	public record ServicoSolicitado(Long servicoId, String nome) {
	}

	public record PecaSolicitada(Long itemId, int quantidade) {
	}
}
