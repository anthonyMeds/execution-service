package br.com.oficina.execution.core.domain;

/**
 * Peca que a execucao precisa consumir. So id e quantidade: o preco e do Billing,
 * e o nome nao e guardado porque nao e exibido na tela do mecanico.
 */
public record PecaRequisitada(Long itemId, int quantidade) {

	public PecaRequisitada {
		if (itemId == null) {
			throw new IllegalArgumentException("itemId e obrigatorio");
		}
		if (quantidade <= 0) {
			throw new IllegalArgumentException("quantidade deve ser maior que zero");
		}
	}
}
