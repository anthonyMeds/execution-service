package br.com.oficina.execution.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.oficina.execution.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica os eventos no RabbitMQ, como JSON, no exchange oficina.eventos.
 *
 * A routing key e "execution." mais o tipo do evento, por exemplo
 * "execution.ExecucaoFinalizada". O OS Service amarra a fila dele em
 * "execution.#" e recebe todos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "rabbit")
public class RabbitEventoPublisher implements EventoPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final JsonMapper jsonMapper;

	@Override
	public void publicar(Envelope envelope) {
		String routingKey = "execution." + envelope.tipo();
		Message mensagem = MessageBuilder
				.withBody(jsonMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.build();

		rabbitTemplate.send(RabbitConfig.EXCHANGE_EVENTOS, routingKey, mensagem);
		log.info("evento publicado tipo={} sagaId={} routingKey={}", envelope.tipo(), envelope.sagaId(), routingKey);
	}
}
