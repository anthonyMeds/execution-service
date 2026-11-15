package br.com.oficina.execution.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import br.com.oficina.execution.exception.TransicaoInvalidaException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A execucao de uma ordem de servico na oficina. Um documento no Mongo por OS.
 *
 * E o equivalente a uma @Entity do JPA: a classe que o repositorio grava e le.
 * A diferenca e que servicos, pecas e historico ficam DENTRO do documento, em
 * vez de em tabelas separadas ligadas por join.
 *
 * Repare que nao existe setter. O status so muda pelos metodos de negocio
 * (iniciar, finalizar, cancelar...), e cada um valida antes de mudar. Se
 * existisse setStatus(), qualquer um poderia finalizar uma execucao com
 * servico pendente.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // o Spring Data precisa de um construtor vazio
@Document(collection = "execucoes")
public class Execucao {

	@Id
	private String id;

	/**
	 * Identificador da saga, criado pelo OS Service. Unico: e o que impede a
	 * mesma mensagem, entregue duas vezes, de criar duas execucoes.
	 */
	@Indexed(unique = true)
	private String sagaId;

	@Indexed(unique = true)
	private Long ordemServicoId;

	private String codigoOrdemServico;
	private StatusExecucao status;
	private String mecanicoResponsavel;
	private List<ServicoExecucao> servicos = new ArrayList<>();
	private List<PecaRequisitada> pecas = new ArrayList<>();
	private List<EventoHistorico> historico = new ArrayList<>();
	private Instant criadaEm;
	private Instant iniciadaEm;
	private Instant finalizadaEm;
	private String motivoEncerramento;

	/** Cria a execucao ja na fila. */
	public Execucao(
			String sagaId,
			Long ordemServicoId,
			String codigoOrdemServico,
			List<ServicoExecucao> servicos,
			List<PecaRequisitada> pecas
	) {
		if (servicos == null || servicos.isEmpty()) {
			throw new IllegalArgumentException("uma execucao precisa de ao menos um servico");
		}
		this.sagaId = sagaId;
		this.ordemServicoId = ordemServicoId;
		this.codigoOrdemServico = codigoOrdemServico;
		this.servicos = new ArrayList<>(servicos);
		this.pecas = pecas == null ? new ArrayList<>() : new ArrayList<>(pecas);
		this.status = StatusExecucao.NA_FILA;
		this.criadaEm = Instant.now();
		registrar("ENFILEIRADA", "entrou na fila com %d servico(s)".formatted(servicos.size()));
	}

	/** Falta peca. Nao e erro: a execucao espera a peca chegar. */
	public void aguardarItens(List<Long> itensEmFalta) {
		exigirNaoEncerrada("aguardar itens");
		this.status = StatusExecucao.AGUARDANDO_ITENS;
		registrar("AGUARDANDO_ITENS", "faltam os itens %s".formatted(itensEmFalta));
	}

	/** Um mecanico pegou o carro da fila. */
	public void iniciar(String mecanico) {
		if (!status.aguardando()) {
			throw new TransicaoInvalidaException(
					"a execucao so inicia a partir de NA_FILA ou AGUARDANDO_ITENS, e esta %s".formatted(status));
		}
		this.status = StatusExecucao.EM_EXECUCAO;
		this.mecanicoResponsavel = mecanico;
		this.iniciadaEm = Instant.now();
		registrar("INICIADA", "mecanico %s".formatted(mecanico));
	}

	/** O mecanico terminou um dos servicos da OS. */
	public ServicoExecucao finalizarServico(Long servicoId, String observacao) {
		exigirEmExecucao("finalizar um servico");
		ServicoExecucao servico = servicos.stream()
				.filter(s -> s.getServicoId().equals(servicoId))
				.findFirst()
				.orElseThrow(() -> new TransicaoInvalidaException(
						"o servico %d nao faz parte desta execucao".formatted(servicoId)));
		servico.concluir(observacao);
		registrar("SERVICO_FINALIZADO", "servico %d (%s)".formatted(servicoId, servico.getNome()));
		return servico;
	}

	/** Fecha a execucao. So passa se todos os servicos estiverem concluidos. */
	public void finalizar() {
		exigirEmExecucao("finalizar a execucao");
		if (!todosOsServicosConcluidos()) {
			throw new TransicaoInvalidaException("ha servicos pendentes nesta execucao");
		}
		this.status = StatusExecucao.FINALIZADA;
		this.finalizadaEm = Instant.now();
		registrar("FINALIZADA", "todos os servicos concluidos");
	}

	/** O OS Service mandou parar (por exemplo, o cliente desistiu). */
	public void cancelar(String motivo) {
		encerrarSemSucesso(StatusExecucao.CANCELADA, motivo);
	}

	/** Algo quebrou no meio do trabalho e nao da para entregar. */
	public void falhar(String motivo) {
		encerrarSemSucesso(StatusExecucao.FALHOU, motivo);
	}

	public boolean todosOsServicosConcluidos() {
		return servicos.stream().allMatch(ServicoExecucao::concluido);
	}

	public long duracaoSegundos() {
		if (iniciadaEm == null || finalizadaEm == null) {
			return 0L;
		}
		return Duration.between(iniciadaEm, finalizadaEm).toSeconds();
	}

	private void encerrarSemSucesso(StatusExecucao novoStatus, String motivo) {
		exigirNaoEncerrada("encerrar");
		servicos.forEach(ServicoExecucao::cancelar);
		this.status = novoStatus;
		this.finalizadaEm = Instant.now();
		this.motivoEncerramento = motivo;
		registrar(novoStatus.name(), motivo);
	}

	private void exigirEmExecucao(String acao) {
		if (status != StatusExecucao.EM_EXECUCAO) {
			throw new TransicaoInvalidaException(
					"so e possivel %s com a execucao EM_EXECUCAO, e ela esta %s".formatted(acao, status));
		}
	}

	private void exigirNaoEncerrada(String acao) {
		if (status.encerrado()) {
			throw new TransicaoInvalidaException(
					"nao e possivel %s uma execucao %s".formatted(acao, status));
		}
	}

	private void registrar(String evento, String detalhe) {
		historico.add(EventoHistorico.agora(evento, detalhe));
	}
}
