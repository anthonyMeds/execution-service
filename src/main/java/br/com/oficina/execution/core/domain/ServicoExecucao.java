package br.com.oficina.execution.core.domain;

import java.time.Duration;
import java.time.Instant;

import br.com.oficina.execution.core.domain.exception.TransicaoInvalidaException;

/**
 * Um servico dentro da execucao. Equivale a linha de servico_ordem_servico da
 * Fase 3, menos o valor_unitario, que ficou no Billing. As datas daqui sao as
 * que alimentam o relatorio de tempo medio.
 */
public class ServicoExecucao {

	private final Long servicoId;
	private final String nome;
	private StatusServicoExecucao status;
	private Instant iniciadoEm;
	private Instant finalizadoEm;
	private String observacao;

	public ServicoExecucao(Long servicoId, String nome) {
		this.servicoId = servicoId;
		this.nome = nome;
		this.status = StatusServicoExecucao.PENDENTE;
	}

	public ServicoExecucao(
			Long servicoId,
			String nome,
			StatusServicoExecucao status,
			Instant iniciadoEm,
			Instant finalizadoEm,
			String observacao
	) {
		this.servicoId = servicoId;
		this.nome = nome;
		this.status = status;
		this.iniciadoEm = iniciadoEm;
		this.finalizadoEm = finalizadoEm;
		this.observacao = observacao;
	}

	public void iniciar() {
		if (status != StatusServicoExecucao.PENDENTE) {
			throw new TransicaoInvalidaException(
					"servico %d so pode ser iniciado quando esta PENDENTE, e esta %s".formatted(servicoId, status));
		}
		this.status = StatusServicoExecucao.EM_ANDAMENTO;
		this.iniciadoEm = Instant.now();
	}

	public void concluir(String observacao) {
		if (status == StatusServicoExecucao.PENDENTE) {
			iniciar();
		}
		if (status != StatusServicoExecucao.EM_ANDAMENTO) {
			throw new TransicaoInvalidaException(
					"servico %d ja esta %s e nao pode ser concluido".formatted(servicoId, status));
		}
		this.status = StatusServicoExecucao.CONCLUIDO;
		this.finalizadoEm = Instant.now();
		this.observacao = observacao;
	}

	public void cancelar() {
		if (status == StatusServicoExecucao.CONCLUIDO) {
			return;
		}
		this.status = StatusServicoExecucao.CANCELADO;
		this.finalizadoEm = Instant.now();
	}

	public boolean concluido() {
		return status == StatusServicoExecucao.CONCLUIDO;
	}

	public boolean pendenteOuEmAndamento() {
		return status == StatusServicoExecucao.PENDENTE || status == StatusServicoExecucao.EM_ANDAMENTO;
	}

	/** Base do calcularTempoMedioServicos que vem do relatorio da Fase 3. */
	public long duracaoMinutos() {
		if (iniciadoEm == null || finalizadoEm == null) {
			return 0L;
		}
		return Duration.between(iniciadoEm, finalizadoEm).toMinutes();
	}

	public Long getServicoId() {
		return servicoId;
	}

	public String getNome() {
		return nome;
	}

	public StatusServicoExecucao getStatus() {
		return status;
	}

	public Instant getIniciadoEm() {
		return iniciadoEm;
	}

	public Instant getFinalizadoEm() {
		return finalizadoEm;
	}

	public String getObservacao() {
		return observacao;
	}
}
