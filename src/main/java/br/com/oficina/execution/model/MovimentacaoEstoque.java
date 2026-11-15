package br.com.oficina.execution.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Um fato sobre o estoque: entrou, foi reservado, foi usado ou foi devolvido.
 * Nunca e alterado depois de gravado.
 *
 * A "referencia" diz o que originou a movimentacao: o sagaId, quando veio de
 * uma OS, ou o numero da nota fiscal, quando e entrada de fornecedor.
 *
 * O indice unico em (referencia, itemId, tipo) e a protecao contra duplicata:
 * a mesma OS nao reserva a mesma peca duas vezes, e a mesma nota fiscal nao
 * entra duas vezes -- o proprio banco recusa.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // o Spring Data precisa de um construtor vazio
@Document(collection = "movimentacoes_estoque")
@CompoundIndex(name = "idx_sem_duplicata", def = "{'referencia': 1, 'itemId': 1, 'tipo': 1}", unique = true)
public class MovimentacaoEstoque {

	@Id
	private String id;

	private String referencia;
	private Long itemId;
	private int quantidade;
	private TipoMovimentacao tipo;

	/** Ja com o sinal: +10 para entrada de dez, -4 para reserva de quatro. */
	private int efeitoNoSaldo;

	private Instant em;

	public MovimentacaoEstoque(String referencia, Long itemId, int quantidade, TipoMovimentacao tipo) {
		this.referencia = referencia;
		this.itemId = itemId;
		this.quantidade = quantidade;
		this.tipo = tipo;
		this.efeitoNoSaldo = tipo.efeitoNoSaldo(quantidade);
		this.em = Instant.now();
	}
}
