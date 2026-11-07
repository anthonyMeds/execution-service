package br.com.oficina.execution.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.com.oficina.execution.core.domain.exception.TransicaoInvalidaException;

/**
 * Raiz do agregado. Um documento no Mongo por ordem de servico.
 *
 * O sagaId e o que torna o consumo idempotente: SQS entrega at-least-once, entao
 * o comando EnfileirarExecucao pode chegar duas vezes, e a segunda tem de ser
 * no-op em vez de criar uma segunda execucao da mesma OS.
 *
 * O dominio nao conhece Mongo nem Spring de proposito: quem traduz para documento
 * e o adapter em adapter/out/mongo. E o que a Fase 3 ja fazia (ADR-0001,
 * Clean Architecture / Ports and Adapters) e o que permite testar tudo isto
 * sem subir banco.
 */
public class Execucao {

	private String id;
	private final String sagaId;
	private final Long ordemServicoId;
	private final String codigoOrdemServico;
	private StatusExecucao status;
	private String mecanicoResponsavel;
	private final List<ServicoExecucao> servicos;
	private final List<PecaRequisitada> pecas;
	private final List<EventoHistorico> historico;
	private final Instant criadaEm;
	private Instant iniciadaEm;
	private Instant finalizadaEm;
	private String motivoEncerramento;

	private Execucao(
			String id,
			String sagaId,
			Long ordemServicoId,
			String codigoOrdemServico,
			StatusExecucao status,
			List<ServicoExecucao> servicos,
			List<PecaRequisitada> pecas,
			List<EventoHistorico> historico,
			Instant criadaEm
	) {
		this.id = id;
		this.sagaId = sagaId;
		this.ordemServicoId = ordemServicoId;
		this.codigoOrdemServico = codigoOrdemServico;
		this.status = status;
		this.servicos = new ArrayList<>(servicos);
		this.pecas = new ArrayList<>(pecas);
		this.historico = new ArrayList<>(historico);
		this.criadaEm = criadaEm;
	}

	/** Entrada na fila, em reacao ao comando EnfileirarExecucao. */
	public static Execucao enfileirar(
			String sagaId,
			Long ordemServicoId,
			String codigoOrdemServico,
			List<ServicoExecucao> servicos,
			List<PecaRequisitada> pecas
	) {
		if (servicos == null || servicos.isEmpty()) {
			throw new IllegalArgumentException("uma execucao precisa de ao menos um servico");
		}
		Execucao execucao = new Execucao(
				null,
				sagaId,
				ordemServicoId,
				codigoOrdemServico,
				StatusExecucao.NA_FILA,
				servicos,
				pecas == null ? List.of() : pecas,
				List.of(),
				Instant.now()
		);
		execucao.registrar("ENFILEIRADA", "entrou na fila com %d servico(s)".formatted(servicos.size()));
		return execucao;
	}

	/** Reconstrucao a partir do documento persistido. Nao valida nem registra historico. */
	public static Execucao reconstituir(
			String id,
			String sagaId,
			Long ordemServicoId,
			String codigoOrdemServico,
			StatusExecucao status,
			String mecanicoResponsavel,
			List<ServicoExecucao> servicos,
			List<PecaRequisitada> pecas,
			List<EventoHistorico> historico,
			Instant criadaEm,
			Instant iniciadaEm,
			Instant finalizadaEm,
			String motivoEncerramento
	) {
		Execucao execucao = new Execucao(
				id, sagaId, ordemServicoId, codigoOrdemServico, status,
				servicos, pecas, historico, criadaEm);
		execucao.mecanicoResponsavel = mecanicoResponsavel;
		execucao.iniciadaEm = iniciadaEm;
		execucao.finalizadaEm = finalizadaEm;
		execucao.motivoEncerramento = motivoEncerramento;
		return execucao;
	}

	/**
	 * Falta peca. Nao e erro nem falha de saga: e o estado AGUARDANDO_ITENS, que
	 * ja existe no StatusOrdemServico da Fase 3 e encaixa exatamente aqui.
	 */
	public void aguardarItens(List<Long> itensEmFalta) {
		exigirNaoEncerrada("aguardar itens");
		this.status = StatusExecucao.AGUARDANDO_ITENS;
		registrar("AGUARDANDO_ITENS", "faltam os itens %s".formatted(itensEmFalta));
	}

	public void iniciar(String mecanico) {
		if (!status.aguardando()) {
			throw new TransicaoInvalidaException(
					"execucao so inicia a partir de NA_FILA ou AGUARDANDO_ITENS, e esta %s".formatted(status));
		}
		this.status = StatusExecucao.EM_EXECUCAO;
		this.mecanicoResponsavel = mecanico;
		this.iniciadaEm = Instant.now();
		registrar("INICIADA", "mecanico %s".formatted(mecanico));
	}

	public void finalizarServico(Long servicoId, String observacao) {
		if (status != StatusExecucao.EM_EXECUCAO) {
			throw new TransicaoInvalidaException(
					"so e possivel finalizar servico com a execucao EM_EXECUCAO, e esta %s".formatted(status));
		}
		ServicoExecucao servico = buscarServico(servicoId);
		servico.concluir(observacao);
		registrar("SERVICO_FINALIZADO", "servico %d em %d min".formatted(servicoId, servico.duracaoMinutos()));
	}

	public void finalizar() {
		if (status != StatusExecucao.EM_EXECUCAO) {
			throw new TransicaoInvalidaException(
					"so e possivel finalizar a execucao quando ela esta EM_EXECUCAO, e esta %s".formatted(status));
		}
		if (!todosOsServicosConcluidos()) {
			throw new TransicaoInvalidaException("ha servicos pendentes nesta execucao");
		}
		this.status = StatusExecucao.FINALIZADA;
		this.finalizadaEm = Instant.now();
		registrar("FINALIZADA", "duracao total de %d min".formatted(duracaoMinutos()));
	}

	/** Compensacao: o OS Service mandou CancelarExecucao. */
	public void cancelar(String motivo) {
		exigirNaoEncerrada("cancelar");
		this.servicos.forEach(ServicoExecucao::cancelar);
		this.status = StatusExecucao.CANCELADA;
		this.finalizadaEm = Instant.now();
		this.motivoEncerramento = motivo;
		registrar("CANCELADA", motivo);
	}

	/** Falha irrecuperavel: dispara o estorno no Mercado Pago, la no Billing. */
	public void falhar(String motivo) {
		exigirNaoEncerrada("falhar");
		this.servicos.forEach(ServicoExecucao::cancelar);
		this.status = StatusExecucao.FALHOU;
		this.finalizadaEm = Instant.now();
		this.motivoEncerramento = motivo;
		registrar("FALHOU", motivo);
	}

	public boolean todosOsServicosConcluidos() {
		return servicos.stream().noneMatch(ServicoExecucao::pendenteOuEmAndamento);
	}

	/** Base do calcularTempoMedioOS que vem do relatorio da Fase 3. */
	public long duracaoMinutos() {
		if (iniciadaEm == null || finalizadaEm == null) {
			return 0L;
		}
		return Duration.between(iniciadaEm, finalizadaEm).toMinutes();
	}

	private ServicoExecucao buscarServico(Long servicoId) {
		return servicos.stream()
				.filter(s -> s.getServicoId().equals(servicoId))
				.findFirst()
				.orElseThrow(() -> new TransicaoInvalidaException(
						"servico %d nao faz parte desta execucao".formatted(servicoId)));
	}

	private void exigirNaoEncerrada(String acao) {
		if (status.encerrado()) {
			throw new TransicaoInvalidaException(
					"nao e possivel %s uma execucao %s".formatted(acao, status));
		}
	}

	private void registrar(String evento, String detalhe) {
		this.historico.add(EventoHistorico.de(evento, detalhe));
	}

	public void atribuirId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public String getSagaId() {
		return sagaId;
	}

	public Long getOrdemServicoId() {
		return ordemServicoId;
	}

	public String getCodigoOrdemServico() {
		return codigoOrdemServico;
	}

	public StatusExecucao getStatus() {
		return status;
	}

	public Optional<String> getMecanicoResponsavel() {
		return Optional.ofNullable(mecanicoResponsavel);
	}

	public List<ServicoExecucao> getServicos() {
		return List.copyOf(servicos);
	}

	public List<PecaRequisitada> getPecas() {
		return List.copyOf(pecas);
	}

	public List<EventoHistorico> getHistorico() {
		return List.copyOf(historico);
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public Instant getIniciadaEm() {
		return iniciadaEm;
	}

	public Instant getFinalizadaEm() {
		return finalizadaEm;
	}

	public String getMotivoEncerramento() {
		return motivoEncerramento;
	}
}
