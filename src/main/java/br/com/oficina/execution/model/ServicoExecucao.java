package br.com.oficina.execution.model;

import java.time.Duration;
import java.time.Instant;

import br.com.oficina.execution.exception.TransicaoInvalidaException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Um servico dentro da execucao (troca de pastilhas, alinhamento...).
 *
 * As datas de inicio e fim daqui sao as que alimentam o relatorio de tempo
 * medio por servico.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // o Spring Data precisa de um construtor vazio
public class ServicoExecucao {

	private Long servicoId;
	private String nome;
	private StatusServicoExecucao status;
	private Instant iniciadoEm;
	private Instant finalizadoEm;
	private String observacao;

	public ServicoExecucao(Long servicoId, String nome) {
		this.servicoId = servicoId;
		this.nome = nome;
		this.status = StatusServicoExecucao.PENDENTE;
	}

	void iniciar() {
		this.status = StatusServicoExecucao.EM_ANDAMENTO;
		this.iniciadoEm = Instant.now();
	}

	void concluir(String observacao) {
		if (status == StatusServicoExecucao.CONCLUIDO || status == StatusServicoExecucao.CANCELADO) {
			throw new TransicaoInvalidaException(
					"o servico %d ja esta %s e nao pode ser concluido".formatted(servicoId, status));
		}
		if (status == StatusServicoExecucao.PENDENTE) {
			iniciar();
		}
		this.status = StatusServicoExecucao.CONCLUIDO;
		this.finalizadoEm = Instant.now();
		this.observacao = observacao;
	}

	void cancelar() {
		if (status != StatusServicoExecucao.CONCLUIDO) {
			this.status = StatusServicoExecucao.CANCELADO;
			this.finalizadoEm = Instant.now();
		}
	}

	public boolean concluido() {
		return status == StatusServicoExecucao.CONCLUIDO;
	}

	public long duracaoSegundos() {
		if (iniciadoEm == null || finalizadoEm == null) {
			return 0L;
		}
		return Duration.between(iniciadoEm, finalizadoEm).toSeconds();
	}
}
