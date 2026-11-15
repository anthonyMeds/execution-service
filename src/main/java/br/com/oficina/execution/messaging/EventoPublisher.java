package br.com.oficina.execution.messaging;

/**
 * Quem sabe publicar um evento.
 *
 * E a unica interface "de troca" do projeto, e existe por um motivo pratico: a
 * tecnologia de mensageria pode mudar (hoje RabbitMQ, talvez SNS + SQS depois).
 * O ExecucaoService so conhece esta interface; qual implementacao entra e
 * decidido pela propriedade oficina.mensageria.tipo:
 *
 *   log    -> LogEventoPublisher     (padrao: so escreve no log, sem broker)
 *   rabbit -> RabbitEventoPublisher
 *
 * Para usar SQS: criar um SqsEventoPublisher com havingValue = "sqs" e um
 * listener que chame o ComandoHandler. Nada alem do pacote messaging muda.
 */
public interface EventoPublisher {

	void publicar(Envelope envelope);
}
