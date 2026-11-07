package br.com.oficina.execution.adapter.out.mongo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * O documento como ele vive no Mongo. Separado do dominio de proposito: assim a
 * anotacao de persistencia nao contamina a Execucao, e mudar o formato guardado
 * nao mexe nas regras.
 *
 * E aqui que se ve por que Mongo: servicos, pecas e historico sao listas aninhadas
 * lidas sempre junto com o cabecalho -- a tela de acompanhamento pede o documento
 * inteiro. Em Postgres isso seria join de quatro tabelas.
 */
@Document(collection = "execucoes")
public class ExecucaoDocument {

	@Id
	private String id;

	/** Unico: e o que garante que o mesmo comando reprocessado nao duplique a execucao. */
	@Indexed(unique = true)
	private String sagaId;

	@Indexed(unique = true)
	private Long ordemServicoId;

	private String codigoOrdemServico;
	private String status;
	private String mecanicoResponsavel;
	private List<ServicoEmbutido> servicos;
	private List<PecaEmbutida> pecas;
	private List<EventoEmbutido> historico;
	private Instant criadaEm;
	private Instant iniciadaEm;
	private Instant finalizadaEm;
	private String motivoEncerramento;

	public static class ServicoEmbutido {
		public Long servicoId;
		public String nome;
		public String status;
		public Instant iniciadoEm;
		public Instant finalizadoEm;
		public String observacao;
	}

	public static class PecaEmbutida {
		public Long itemId;
		public int quantidade;
	}

	public static class EventoEmbutido {
		public String evento;
		public Instant em;
		public String detalhe;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSagaId() {
		return sagaId;
	}

	public void setSagaId(String sagaId) {
		this.sagaId = sagaId;
	}

	public Long getOrdemServicoId() {
		return ordemServicoId;
	}

	public void setOrdemServicoId(Long ordemServicoId) {
		this.ordemServicoId = ordemServicoId;
	}

	public String getCodigoOrdemServico() {
		return codigoOrdemServico;
	}

	public void setCodigoOrdemServico(String codigoOrdemServico) {
		this.codigoOrdemServico = codigoOrdemServico;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMecanicoResponsavel() {
		return mecanicoResponsavel;
	}

	public void setMecanicoResponsavel(String mecanicoResponsavel) {
		this.mecanicoResponsavel = mecanicoResponsavel;
	}

	public List<ServicoEmbutido> getServicos() {
		return servicos;
	}

	public void setServicos(List<ServicoEmbutido> servicos) {
		this.servicos = servicos;
	}

	public List<PecaEmbutida> getPecas() {
		return pecas;
	}

	public void setPecas(List<PecaEmbutida> pecas) {
		this.pecas = pecas;
	}

	public List<EventoEmbutido> getHistorico() {
		return historico;
	}

	public void setHistorico(List<EventoEmbutido> historico) {
		this.historico = historico;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public void setCriadaEm(Instant criadaEm) {
		this.criadaEm = criadaEm;
	}

	public Instant getIniciadaEm() {
		return iniciadaEm;
	}

	public void setIniciadaEm(Instant iniciadaEm) {
		this.iniciadaEm = iniciadaEm;
	}

	public Instant getFinalizadaEm() {
		return finalizadaEm;
	}

	public void setFinalizadaEm(Instant finalizadaEm) {
		this.finalizadaEm = finalizadaEm;
	}

	public String getMotivoEncerramento() {
		return motivoEncerramento;
	}

	public void setMotivoEncerramento(String motivoEncerramento) {
		this.motivoEncerramento = motivoEncerramento;
	}
}
