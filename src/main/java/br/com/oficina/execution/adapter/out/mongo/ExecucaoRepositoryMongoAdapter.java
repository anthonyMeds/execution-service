package br.com.oficina.execution.adapter.out.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.port.out.ExecucaoRepositoryPort;

@Component
public class ExecucaoRepositoryMongoAdapter implements ExecucaoRepositoryPort {

	private final ExecucaoMongoRepository repositorio;

	public ExecucaoRepositoryMongoAdapter(ExecucaoMongoRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	public Execucao salvar(Execucao execucao) {
		ExecucaoDocument salvo = repositorio.save(ExecucaoDocumentMapper.paraDocumento(execucao));
		execucao.atribuirId(salvo.getId());
		return ExecucaoDocumentMapper.paraDominio(salvo);
	}

	@Override
	public Optional<Execucao> porOrdemServico(Long ordemServicoId) {
		return repositorio.findByOrdemServicoId(ordemServicoId).map(ExecucaoDocumentMapper::paraDominio);
	}

	@Override
	public Optional<Execucao> porSagaId(String sagaId) {
		return repositorio.findBySagaId(sagaId).map(ExecucaoDocumentMapper::paraDominio);
	}

	@Override
	public List<Execucao> fila() {
		return repositorio.buscarFilaOrdenadaPorChegada(Sort.by(Sort.Direction.ASC, "criadaEm"))
				.stream()
				.map(ExecucaoDocumentMapper::paraDominio)
				.toList();
	}
}
