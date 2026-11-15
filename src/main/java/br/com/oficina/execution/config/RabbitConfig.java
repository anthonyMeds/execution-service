package br.com.oficina.execution.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria no RabbitMQ o que este servico precisa. So vale quando
 * oficina.mensageria.tipo=rabbit.
 *
 *   exchange oficina.comandos --(execution.#)--> fila oficina-execution-comandos
 *   exchange oficina.eventos  <-- onde este servico publica
 *
 * Os dois exchanges sao compartilhados pelos tres servicos; declarar um que ja
 * existe nao da erro, entao cada servico declara os dois ao subir.
 */
@Configuration
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "rabbit")
public class RabbitConfig {

	public static final String EXCHANGE_COMANDOS = "oficina.comandos";
	public static final String EXCHANGE_EVENTOS = "oficina.eventos";

	public static final String FILA_COMANDOS = "oficina-execution-comandos";
	public static final String FILA_COMANDOS_DLQ = "oficina-execution-comandos-dlq";

	@Bean
	TopicExchange exchangeComandos() {
		return new TopicExchange(EXCHANGE_COMANDOS);
	}

	@Bean
	TopicExchange exchangeEventos() {
		return new TopicExchange(EXCHANGE_EVENTOS);
	}

	/** Para onde vai a mensagem que falhou em todas as tentativas. */
	@Bean
	Queue filaComandosDlq() {
		return QueueBuilder.durable(FILA_COMANDOS_DLQ).build();
	}

	@Bean
	Queue filaComandos() {
		return QueueBuilder.durable(FILA_COMANDOS)
				.withArgument("x-dead-letter-exchange", "")
				.withArgument("x-dead-letter-routing-key", FILA_COMANDOS_DLQ)
				.build();
	}

	/** Tudo que chegar em oficina.comandos com routing key "execution.*" cai na fila deste servico. */
	@Bean
	Binding bindingComandos() {
		return BindingBuilder.bind(filaComandos()).to(exchangeComandos()).with("execution.#");
	}
}
