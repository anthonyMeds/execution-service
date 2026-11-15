package br.com.oficina.execution.model;

/**
 * Peca que a execucao precisa. So id e quantidade: nome e preco sao do
 * catalogo, que fica no Billing.
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
