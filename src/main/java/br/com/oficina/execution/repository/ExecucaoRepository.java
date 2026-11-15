package br.com.oficina.execution.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.StatusExecucao;

/**
 * Funciona como um JpaRepository: o Spring cria a implementacao sozinho, e
 * monta a consulta a partir do nome do metodo.
 */
public interface ExecucaoRepository extends MongoRepository<Execucao, String> {

	Optional<Execucao> findByOrdemServicoId(Long ordemServicoId);

	Optional<Execucao> findBySagaId(String sagaId);

	/** A fila: quem tem um destes status, do mais antigo para o mais novo. */
	List<Execucao> findByStatusInOrderByCriadaEmAsc(Collection<StatusExecucao> status);

	List<Execucao> findByStatus(StatusExecucao status);
}
