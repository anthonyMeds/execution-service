package br.com.oficina.execution.adapter.out.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovimentacaoMongoRepository extends MongoRepository<MovimentacaoDocument, String> {

	boolean existsBySagaIdAndItemIdAndTipo(String sagaId, Long itemId, String tipo);

	List<MovimentacaoDocument> findByItemId(Long itemId);
}
