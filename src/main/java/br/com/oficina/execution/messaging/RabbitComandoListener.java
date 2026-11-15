package br.com.oficina.execution.messaging;

import java.nio.charset.StandardCharsets;

import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.oficina.execution.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tira os comandos da fila do RabbitMQ e entrega ao ComandoHandler.
 *
 * E so isso de proposito: toda a logica esta no handler, que nao sabe de onde a
 * mensagem veio. Trocar de broker e trocar esta classe.
 *
 * Se o processamento der erro, a mensagem NAO volta para a fila (voltaria e daria
 * o mesmo erro, para sempre). Ela e rejeitada, e o RabbitMQ a move para a fila
 * oficina-execution-comandos-dlq, onde fica guardada para alguem olhar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "rabbit")
public class RabbitComandoListener {

	private final ComandoHandler comandoHandler;

	@RabbitListener(queues = RabbitConfig.FILA_COMANDOS)
	public void receber(Message mensagem) {
		String json = new String(mensagem.getBody(), StandardCharsets.UTF_8);

		// Guarda o trace de quem mandou o comando; o RabbitEventoPublisher copia
		// esses valores para o evento que sair em resposta.
		for (String cabecalho : Rastreamento.CABECALHOS) {
			Object valor = mensagem.getMessageProperties().getHeader(cabecalho);
			if (valor != null) {
				MDC.put(cabecalho, valor.toString());
			}
		}
		try {
			comandoHandler.tratar(json);
		} catch (RuntimeException e) {
			log.error("comando com erro, enviado para a DLQ: {}", json, e);
			throw new AmqpRejectAndDontRequeueException("comando rejeitado", e);
		} finally {
			Rastreamento.CABECALHOS.forEach(MDC::remove);
		}
	}
}
