package br.com.oficina.execution.core.port.out;

import br.com.oficina.execution.core.evento.EnvelopeEvento;

/**
 * Porta de saida da mensageria.
 *
 * Existe justamente porque a escolha entre SNS+SQS e RabbitMQ ainda depende do
 * teste de permissao da LabRole. O dominio publica contra esta interface e nao
 * sabe o que ha do outro lado; trocar o adapter depois e meia hora de trabalho,
 * enquanto um dominio que soubesse de SQS custaria semanas de retrabalho.
 */
public interface EventoPublisherPort {

	void publicar(EnvelopeEvento envelope);
}
