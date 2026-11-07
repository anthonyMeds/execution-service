package br.com.oficina.execution.adapter.out.mongo;

import org.springframework.stereotype.Component;

import br.com.oficina.execution.core.domain.MovimentacaoEstoque;
import br.com.oficina.execution.core.domain.TipoMovimentacao;
import br.com.oficina.execution.core.port.out.MovimentacaoEstoqueRepositoryPort;

/**
 * O saldo e somado a partir das movimentacoes, nunca lido de um campo.
 *
 * Para o volume deste trabalho somar em memoria e suficiente e deixa o codigo
 * obvio. Se a colecao crescer, o passo seguinte e uma agregacao com $group no
 * proprio Mongo -- e so esta classe muda.
 */
@Component
public class MovimentacaoEstoqueRepositoryMongoAdapter implements MovimentacaoEstoqueRepositoryPort {

	private final MovimentacaoMongoRepository repositorio;

	public MovimentacaoEstoqueRepositoryMongoAdapter(MovimentacaoMongoRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	public MovimentacaoEstoque registrar(MovimentacaoEstoque movimentacao) {
		MovimentacaoDocument doc = new MovimentacaoDocument();
		doc.setSagaId(movimentacao.sagaId());
		doc.setItemId(movimentacao.itemId());
		doc.setQuantidade(movimentacao.quantidade());
		doc.setTipo(movimentacao.tipo().name());
		doc.setEfeitoNoSaldo(movimentacao.efeitoNoSaldo());
		doc.setEm(movimentacao.em());

		MovimentacaoDocument salvo = repositorio.save(doc);
		return new MovimentacaoEstoque(
				salvo.getId(),
				salvo.getSagaId(),
				salvo.getItemId(),
				salvo.getQuantidade(),
				TipoMovimentacao.valueOf(salvo.getTipo()),
				salvo.getEm());
	}

	@Override
	public boolean jaRegistrada(String sagaId, Long itemId, TipoMovimentacao tipo) {
		return repositorio.existsBySagaIdAndItemIdAndTipo(sagaId, itemId, tipo.name());
	}

	@Override
	public int saldoDe(Long itemId) {
		return repositorio.findByItemId(itemId).stream()
				.mapToInt(MovimentacaoDocument::getEfeitoNoSaldo)
				.sum();
	}
}
