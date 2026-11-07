package br.com.oficina.execution.adapter.out.evento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.oficina.execution.core.evento.EnvelopeEvento;
import br.com.oficina.execution.core.port.out.EventoPublisherPort;

/**
 * Adapter provisorio: por enquanto so registra o evento no log.
 *
 * Existe para o servico subir e ser testado de ponta a ponta antes de a
 * mensageria estar decidida -- a escolha entre SNS+SQS e RabbitMQ depende do
 * teste de permissao da LabRole no cluster. Quando a decisao sair, entra um
 * EventoPublisherSqs (ou Rabbit) implementando a mesma porta, e esta classe
 * continua servindo ao perfil local e aos testes.
 */
@Component
public class EventoPublisherLog implements EventoPublisherPort {

	private static final Logger log = LoggerFactory.getLogger(EventoPublisherLog.class);

	@Override
	public void publicar(EnvelopeEvento envelope) {
		if (log.isInfoEnabled()) {
			log.info("evento={} sagaId={} ordemServicoId={} dados={}",
					envelope.tipo(), envelope.sagaId(), envelope.ordemServicoId(), envelope.dados());
		}
	}
}
