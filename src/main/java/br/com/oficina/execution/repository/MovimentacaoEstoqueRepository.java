package br.com.oficina.execution.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.oficina.execution.model.MovimentacaoEstoque;
import br.com.oficina.execution.model.TipoMovimentacao;

public interface MovimentacaoEstoqueRepository extends MongoRepository<MovimentacaoEstoque, String> {

	boolean existsByReferenciaAndItemIdAndTipo(String referencia, Long itemId, TipoMovimentacao tipo);

	List<MovimentacaoEstoque> findByItemId(Long itemId);
}
