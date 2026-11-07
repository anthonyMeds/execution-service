package br.com.oficina.execution.adapter.out.mongo;

import java.util.ArrayList;
import java.util.List;

import br.com.oficina.execution.core.domain.EventoHistorico;
import br.com.oficina.execution.core.domain.Execucao;
import br.com.oficina.execution.core.domain.PecaRequisitada;
import br.com.oficina.execution.core.domain.ServicoExecucao;
import br.com.oficina.execution.core.domain.StatusExecucao;
import br.com.oficina.execution.core.domain.StatusServicoExecucao;

/** Traducao entre o agregado e o documento. Fica no adapter, nunca no dominio. */
final class ExecucaoDocumentMapper {

	private ExecucaoDocumentMapper() {
	}

	static ExecucaoDocument paraDocumento(Execucao execucao) {
		ExecucaoDocument doc = new ExecucaoDocument();
		doc.setId(execucao.getId());
		doc.setSagaId(execucao.getSagaId());
		doc.setOrdemServicoId(execucao.getOrdemServicoId());
		doc.setCodigoOrdemServico(execucao.getCodigoOrdemServico());
		doc.setStatus(execucao.getStatus().name());
		doc.setMecanicoResponsavel(execucao.getMecanicoResponsavel().orElse(null));
		doc.setCriadaEm(execucao.getCriadaEm());
		doc.setIniciadaEm(execucao.getIniciadaEm());
		doc.setFinalizadaEm(execucao.getFinalizadaEm());
		doc.setMotivoEncerramento(execucao.getMotivoEncerramento());

		List<ExecucaoDocument.ServicoEmbutido> servicos = new ArrayList<>();
		for (ServicoExecucao s : execucao.getServicos()) {
			ExecucaoDocument.ServicoEmbutido e = new ExecucaoDocument.ServicoEmbutido();
			e.servicoId = s.getServicoId();
			e.nome = s.getNome();
			e.status = s.getStatus().name();
			e.iniciadoEm = s.getIniciadoEm();
			e.finalizadoEm = s.getFinalizadoEm();
			e.observacao = s.getObservacao();
			servicos.add(e);
		}
		doc.setServicos(servicos);

		List<ExecucaoDocument.PecaEmbutida> pecas = new ArrayList<>();
		for (PecaRequisitada p : execucao.getPecas()) {
			ExecucaoDocument.PecaEmbutida e = new ExecucaoDocument.PecaEmbutida();
			e.itemId = p.itemId();
			e.quantidade = p.quantidade();
			pecas.add(e);
		}
		doc.setPecas(pecas);

		List<ExecucaoDocument.EventoEmbutido> historico = new ArrayList<>();
		for (EventoHistorico h : execucao.getHistorico()) {
			ExecucaoDocument.EventoEmbutido e = new ExecucaoDocument.EventoEmbutido();
			e.evento = h.evento();
			e.em = h.em();
			e.detalhe = h.detalhe();
			historico.add(e);
		}
		doc.setHistorico(historico);

		return doc;
	}

	static Execucao paraDominio(ExecucaoDocument doc) {
		List<ServicoExecucao> servicos = new ArrayList<>();
		if (doc.getServicos() != null) {
			for (ExecucaoDocument.ServicoEmbutido e : doc.getServicos()) {
				servicos.add(new ServicoExecucao(
						e.servicoId,
						e.nome,
						StatusServicoExecucao.valueOf(e.status),
						e.iniciadoEm,
						e.finalizadoEm,
						e.observacao));
			}
		}

		List<PecaRequisitada> pecas = new ArrayList<>();
		if (doc.getPecas() != null) {
			for (ExecucaoDocument.PecaEmbutida e : doc.getPecas()) {
				pecas.add(new PecaRequisitada(e.itemId, e.quantidade));
			}
		}

		List<EventoHistorico> historico = new ArrayList<>();
		if (doc.getHistorico() != null) {
			for (ExecucaoDocument.EventoEmbutido e : doc.getHistorico()) {
				historico.add(new EventoHistorico(e.evento, e.em, e.detalhe));
			}
		}

		return Execucao.reconstituir(
				doc.getId(),
				doc.getSagaId(),
				doc.getOrdemServicoId(),
				doc.getCodigoOrdemServico(),
				StatusExecucao.valueOf(doc.getStatus()),
				doc.getMecanicoResponsavel(),
				servicos,
				pecas,
				historico,
				doc.getCriadaEm(),
				doc.getIniciadaEm(),
				doc.getFinalizadaEm(),
				doc.getMotivoEncerramento());
	}
}
