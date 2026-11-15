package br.com.oficina.execution.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.exception.TransicaoInvalidaException;
import br.com.oficina.execution.messaging.Envelope;
import br.com.oficina.execution.messaging.EventoPublisher;
import br.com.oficina.execution.messaging.TipoEvento;
import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.model.StatusExecucao;
import br.com.oficina.execution.repository.ExecucaoRepository;
import lombok.RequiredArgsConstructor;

/**
 * As acoes sobre a execucao de uma OS.
 *
 * Todo metodo que muda estado segue os mesmos quatro passos:
 *   1. busca a execucao
 *   2. pede para ela mudar (a regra mora na classe Execucao)
 *   3. salva
 *   4. publica o evento que avisa o OS Service
 */
@Service
@RequiredArgsConstructor
public class ExecucaoService {

	private static final List<StatusExecucao> STATUS_DA_FILA =
			List.of(StatusExecucao.NA_FILA, StatusExecucao.AGUARDANDO_ITENS);

	private final ExecucaoRepository execucaoRepository;
	private final EstoqueService estoqueService;
	private final EventoPublisher eventoPublisher;

	/**
	 * Entrada da saga neste servico: o OS Service avisou que a OS foi paga e
	 * pode ir para a oficina.
	 */
	public Execucao enfileirar(
			String sagaId,
			Long ordemServicoId,
			String codigoOrdemServico,
			List<ServicoExecucao> servicos,
			List<PecaRequisitada> pecas
	) {
		// A fila de mensagens pode entregar a mesma mensagem duas vezes. Se esta
		// saga ja tem execucao, devolve a que existe em vez de criar outra.
		Optional<Execucao> jaExiste = execucaoRepository.findBySagaId(sagaId);
		if (jaExiste.isPresent()) {
			return jaExiste.get();
		}

		Execucao execucao = new Execucao(sagaId, ordemServicoId, codigoOrdemServico, servicos, pecas);

		List<Long> itensEmFalta = estoqueService.reservar(sagaId, execucao.getPecas());
		if (!itensEmFalta.isEmpty()) {
			execucao.aguardarItens(itensEmFalta);
		}

		Execucao salva = execucaoRepository.save(execucao);

		if (!itensEmFalta.isEmpty()) {
			publicar(salva, TipoEvento.EXECUCAO_AGUARDANDO_ITENS, Map.of("itensEmFalta", itensEmFalta));
		}
		return salva;
	}

	/** O mecanico pegou o carro da fila. */
	public Execucao iniciar(Long ordemServicoId, String mecanico) {
		Execucao execucao = buscarPorOrdemServico(ordemServicoId);

		// Se a execucao estava esperando peca, tenta reservar de novo: a peca
		// pode ter chegado. O que ja estava reservado nao e reservado outra vez.
		if (execucao.getStatus().aguardando()) {
			List<Long> itensEmFalta = estoqueService.reservar(execucao.getSagaId(), execucao.getPecas());
			if (!itensEmFalta.isEmpty()) {
				throw new TransicaoInvalidaException(
						"ainda faltam em estoque os itens %s".formatted(itensEmFalta));
			}
		}

		execucao.iniciar(mecanico);
		Execucao salva = execucaoRepository.save(execucao);

		Map<String, Object> dados = new LinkedHashMap<>();
		dados.put("execucaoId", salva.getId());
		dados.put("mecanico", mecanico);
		dados.put("iniciadoEm", salva.getIniciadaEm());
		publicar(salva, TipoEvento.EXECUCAO_INICIADA, dados);
		return salva;
	}

	/** O mecanico terminou um dos servicos da OS. */
	public Execucao finalizarServico(Long ordemServicoId, Long servicoId, String observacao) {
		Execucao execucao = buscarPorOrdemServico(ordemServicoId);

		ServicoExecucao servico = execucao.finalizarServico(servicoId, observacao);
		Execucao salva = execucaoRepository.save(execucao);

		Map<String, Object> dados = new LinkedHashMap<>();
		dados.put("execucaoId", salva.getId());
		dados.put("servicoId", servicoId);
		dados.put("duracaoMinutos", servico.duracaoSegundos() / 60);
		publicar(salva, TipoEvento.SERVICO_FINALIZADO, dados);
		return salva;
	}

	/** Todos os servicos prontos: fecha a execucao e consome as pecas. */
	public Execucao finalizar(Long ordemServicoId) {
		Execucao execucao = buscarPorOrdemServico(ordemServicoId);

		execucao.finalizar();
		estoqueService.darBaixa(execucao.getSagaId(), execucao.getPecas());
		Execucao salva = execucaoRepository.save(execucao);

		Map<String, Object> dados = new LinkedHashMap<>();
		dados.put("execucaoId", salva.getId());
		dados.put("duracaoTotalMinutos", salva.duracaoSegundos() / 60);
		dados.put("servicosExecutados", salva.getServicos().stream().map(ServicoExecucao::getServicoId).toList());
		publicar(salva, TipoEvento.EXECUCAO_FINALIZADA, dados);
		return salva;
	}

	/**
	 * Compensacao vinda de fora: o OS Service mandou cancelar. Devolve as pecas
	 * e nao publica evento, porque quem pediu o cancelamento ja sabe dele.
	 */
	public Execucao cancelar(Long ordemServicoId, String motivo) {
		Execucao execucao = buscarPorOrdemServico(ordemServicoId);

		// A mesma mensagem de cancelamento pode chegar duas vezes. Na segunda,
		// nao ha nada a fazer -- e nao pode dar erro, senao a mensagem iria
		// parar na fila de mensagens com defeito sem ter defeito nenhum.
		if (execucao.getStatus() == StatusExecucao.CANCELADA) {
			return execucao;
		}

		execucao.cancelar(motivo);
		estoqueService.devolver(execucao.getSagaId(), execucao.getPecas());
		return execucaoRepository.save(execucao);
	}

	/**
	 * A execucao quebrou aqui dentro. Devolve as pecas e publica ExecucaoFalhou,
	 * que e o que leva o OS Service a pedir o estorno do pagamento ao Billing.
	 */
	public Execucao falhar(Long ordemServicoId, String motivo, String etapa) {
		Execucao execucao = buscarPorOrdemServico(ordemServicoId);

		execucao.falhar(motivo);
		estoqueService.devolver(execucao.getSagaId(), execucao.getPecas());
		Execucao salva = execucaoRepository.save(execucao);

		Map<String, Object> dados = new LinkedHashMap<>();
		dados.put("motivo", motivo);
		dados.put("etapa", etapa);
		publicar(salva, TipoEvento.EXECUCAO_FALHOU, dados);
		return salva;
	}

	public Execucao buscarPorOrdemServico(Long ordemServicoId) {
		return execucaoRepository.findByOrdemServicoId(ordemServicoId)
				.orElseThrow(() -> new ExecucaoNaoEncontradaException(ordemServicoId));
	}

	/** A fila da oficina: quem ainda nao comecou, do mais antigo para o mais novo. */
	public List<Execucao> listarFila() {
		return execucaoRepository.findByStatusInOrderByCriadaEmAsc(STATUS_DA_FILA);
	}

	private void publicar(Execucao execucao, String tipo, Map<String, Object> dados) {
		eventoPublisher.publicar(Envelope.de(execucao, tipo, dados));
	}
}
