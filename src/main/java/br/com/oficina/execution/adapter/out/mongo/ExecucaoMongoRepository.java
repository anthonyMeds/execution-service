package br.com.oficina.execution.adapter.out.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ExecucaoMongoRepository extends MongoRepository<ExecucaoDocument, String> {

	Optional<ExecucaoDocument> findByOrdemServicoId(Long ordemServicoId);

	Optional<ExecucaoDocument> findBySagaId(String sagaId);

	@Query("{ 'status': { $in: ['NA_FILA', 'AGUARDANDO_ITENS'] } }")
	List<ExecucaoDocument> buscarFilaOrdenadaPorChegada(org.springframework.data.domain.Sort sort);
}
