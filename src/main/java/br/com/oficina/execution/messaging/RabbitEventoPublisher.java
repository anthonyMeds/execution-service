package br.com.oficina.execution.messaging;

import java.nio.charset.StandardCharsets;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.oficina.execution.config.CorrelationIdFilter;
import br.com.oficina.execution.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica os eventos no exchange execucao.eventos, com a routing key igual ao
 * tipo do evento (ExecucaoIniciada, ExecucaoFinalizada...). E nesse formato que
 * a fila do OS Service esta ligada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "rabbit")
public class RabbitEventoPublisher implements EventoPublisher {

	private static final String APLICACAO = "oficina-execution-service";
	private static final String CABECALHO_ORDEM_SERVICO = "x-ordem-servico-id";

	private final RabbitTemplate rabbitTemplate;
	private final JsonMapper jsonMapper;

	@Override
	public void publicar(Envelope envelope) {
		MessageBuilder construtor = MessageBuilder
				.withBody(jsonMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8));

		// Se este evento e resposta a um comando, continua o trace de quem mandou o comando.
		for (String cabecalho : Rastreamento.CABECALHOS) {
			String valor = MDC.get(cabecalho);
			if (valor != null) {
				construtor.setHeader(cabecalho, valor);
			}
		}

		Message mensagem = construtor
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setMessageId(envelope.messageId())
				.setType(envelope.tipo())
				.setCorrelationId(envelope.correlationId())
				.setHeader(CorrelationIdFilter.HEADER, envelope.correlationId())
				.setHeader(CABECALHO_ORDEM_SERVICO, envelope.ordemServicoId())
				.setAppId(APLICACAO)
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();

		rabbitTemplate.send(RabbitConfig.EXCHANGE_EXECUCAO, envelope.tipo(), mensagem);
		log.info("evento publicado tipo={} sagaId={} ordemServicoId={} messageId={}",
				envelope.tipo(), envelope.sagaId(), envelope.ordemServicoId(), envelope.messageId());
	}
}
