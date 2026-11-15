package br.com.oficina.execution.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Nao publica em lugar nenhum: so escreve o evento no log.
 *
 * E o padrao, para o servico subir sem broker em desenvolvimento e nos testes.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "log", matchIfMissing = true)
public class LogEventoPublisher implements EventoPublisher {

	@Override
	public void publicar(Envelope envelope) {
		log.info("[mensageria=log] evento={} sagaId={} ordemServicoId={} dados={}",
				envelope.tipo(), envelope.sagaId(), envelope.ordemServicoId(), envelope.dados());
	}
}
