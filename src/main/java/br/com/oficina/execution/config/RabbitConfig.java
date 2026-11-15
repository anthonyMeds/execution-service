package br.com.oficina.execution.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.oficina.execution.messaging.TipoComando;

/**
 * Cria no RabbitMQ o que este servico precisa, na mesma topologia do OS Service
 * e do Billing: cada servico publica no proprio exchange, e a routing key e o
 * nome da mensagem.
 *
 * <pre>
 * os.eventos (topic) --EnfileirarExecucao, CancelarExecucao--> oficina-execution-comandos
 *                                       rejeitada --> oficina-execution-comandos-dlx --> oficina-execution-comandos-dlq
 * execucao.eventos (topic) <-- este servico publica (routing key = tipo do evento); o OS Service consome
 * </pre>
 *
 * Declarar um exchange que ja existe nao da erro (desde que com as mesmas
 * opcoes), entao nao importa qual servico sobe primeiro. So vale com
 * oficina.mensageria.tipo=rabbit.
 */
@Configuration
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "rabbit")
public class RabbitConfig {

	/** Onde o OS Service publica os comandos. */
	public static final String EXCHANGE_OS = "os.eventos";

	/** Onde este servico publica os eventos. O OS Service ja tem a fila dele ligada aqui. */
	public static final String EXCHANGE_EXECUCAO = "execucao.eventos";

	public static final String FILA_COMANDOS = "oficina-execution-comandos";
	public static final String EXCHANGE_DEAD_LETTER = FILA_COMANDOS + "-dlx";
	public static final String FILA_COMANDOS_DLQ = FILA_COMANDOS + "-dlq";

	@Bean
	Declarables topologiaMensageria() {
		TopicExchange exchangeOs = new TopicExchange(EXCHANGE_OS, true, false);
		TopicExchange exchangeExecucao = new TopicExchange(EXCHANGE_EXECUCAO, true, false);
		DirectExchange deadLetter = new DirectExchange(EXCHANGE_DEAD_LETTER, true, false);

		// Mensagem que da erro no processamento sai da fila e vai para a DLQ, em vez
		// de voltar e dar o mesmo erro para sempre.
		Queue filaComandos = QueueBuilder.durable(FILA_COMANDOS)
				.deadLetterExchange(EXCHANGE_DEAD_LETTER)
				.deadLetterRoutingKey(FILA_COMANDOS_DLQ)
				.build();
		Queue filaDlq = QueueBuilder.durable(FILA_COMANDOS_DLQ).build();

		return new Declarables(
				exchangeOs,
				exchangeExecucao,
				deadLetter,
				filaComandos,
				filaDlq,
				BindingBuilder.bind(filaDlq).to(deadLetter).with(FILA_COMANDOS_DLQ),
				// So os dois comandos destinados a execucao; o resto do que o OS publica
				// em os.eventos (para o Billing, por exemplo) nao chega aqui.
				BindingBuilder.bind(filaComandos).to(exchangeOs).with(TipoComando.ENFILEIRAR_EXECUCAO),
				BindingBuilder.bind(filaComandos).to(exchangeOs).with(TipoComando.CANCELAR_EXECUCAO));
	}
}
