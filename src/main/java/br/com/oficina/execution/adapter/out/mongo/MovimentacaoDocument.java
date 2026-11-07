package br.com.oficina.execution.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Movimentacao de estoque, append-only. O indice composto e unico sobre
 * (sagaId, itemId, tipo) e a garantia de idempotencia no proprio banco: mesmo
 * que dois pods processem a mesma mensagem ao mesmo tempo, so um registro entra.
 */
@Document(collection = "movimentacoes_estoque")
@CompoundIndex(name = "idx_idempotencia", def = "{'sagaId': 1, 'itemId': 1, 'tipo': 1}", unique = true)
public class MovimentacaoDocument {

	@Id
	private String id;

	private String sagaId;
	private Long itemId;
	private int quantidade;
	private String tipo;
	private int efeitoNoSaldo;
	private Instant em;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSagaId() {
		return sagaId;
	}

	public void setSagaId(String sagaId) {
		this.sagaId = sagaId;
	}

	public Long getItemId() {
		return itemId;
	}

	public void setItemId(Long itemId) {
		this.itemId = itemId;
	}

	public int getQuantidade() {
		return quantidade;
	}

	public void setQuantidade(int quantidade) {
		this.quantidade = quantidade;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public int getEfeitoNoSaldo() {
		return efeitoNoSaldo;
	}

	public void setEfeitoNoSaldo(int efeitoNoSaldo) {
		this.efeitoNoSaldo = efeitoNoSaldo;
	}

	public Instant getEm() {
		return em;
	}

	public void setEm(Instant em) {
		this.em = em;
	}
}
