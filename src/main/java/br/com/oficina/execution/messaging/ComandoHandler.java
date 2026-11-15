package br.com.oficina.execution.messaging;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.oficina.execution.config.CorrelationIdFilter;
import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.service.ExecucaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe o JSON de um comando, descobre qual e, e chama o ExecucaoService.
 *
 * Nao sabe de onde a mensagem veio. Quem a tira da fila (hoje o
 * RabbitComandoListener, amanha talvez um listener de SQS) so entrega o texto
 * aqui. Por isso trocar de tecnologia de fila nao mexe nesta classe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComandoHandler {

	private final ExecucaoService execucaoService;
	private final JsonMapper jsonMapper;

	public void tratar(String json) {
		Envelope envelope = jsonMapper.readValue(json, Envelope.class);

		// Poe o correlationId da mensagem no log, para o rastro nao se perder
		// quando a requisicao atravessa a fila.
		if (envelope.correlationId() != null) {
			MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
		}
		try {
			log.info("comando recebido tipo={} sagaId={} ordemServicoId={}",
					envelope.tipo(), envelope.sagaId(), envelope.ordemServicoId());

			switch (envelope.tipo()) {
				case TipoComando.ENFILEIRAR_EXECUCAO -> enfileirar(envelope);
				case TipoComando.CANCELAR_EXECUCAO -> cancelar(envelope);
				default -> log.warn("comando desconhecido, ignorado: tipo={}", envelope.tipo());
			}
		} finally {
			MDC.remove(CorrelationIdFilter.MDC_KEY);
		}
	}

	private void enfileirar(Envelope envelope) {
		DadosEnfileirar dados = jsonMapper.convertValue(envelope.dados(), DadosEnfileirar.class);

		List<ServicoExecucao> servicos = dados.servicos().stream()
				.map(s -> new ServicoExecucao(s.servicoId(), s.nome()))
				.toList();
		List<PecaRequisitada> pecas = dados.itens().stream()
				.map(i -> new PecaRequisitada(i.itemId(), i.quantidade()))
				.toList();

		execucaoService.enfileirar(
				envelope.sagaId(),
				envelope.ordemServicoId(),
				envelope.codigoOrdemServico(),
				servicos,
				pecas);
	}

	private void cancelar(Envelope envelope) {
		Object motivo = envelope.dados() == null ? null : envelope.dados().get("motivo");
		try {
			execucaoService.cancelar(
					envelope.ordemServicoId(),
					motivo == null ? "cancelado pelo OS Service" : motivo.toString());
		} catch (ExecucaoNaoEncontradaException e) {
			// Cancelar o que nunca chegou a existir aqui nao e erro: a saga foi
			// desfeita antes de a OS entrar na oficina.
			log.warn("cancelamento ignorado: {}", e.getMessage());
		}
	}

	/** O conteudo de "dados" no comando EnfileirarExecucao. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record DadosEnfileirar(List<Servico> servicos, List<Item> itens) {

		DadosEnfileirar {
			servicos = servicos == null ? List.of() : servicos;
			itens = itens == null ? List.of() : itens;
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Servico(Long servicoId, String nome) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Item(Long itemId, int quantidade) {
		}
	}
}
