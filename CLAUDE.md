# Instrucoes para assistentes de IA neste repositorio

## Regra que nao se quebra

**Nunca executar `git commit` nem `git push` sem permissao explicita do Anthony
na mesma conversa.** Editar e criar arquivos e sempre permitido; `git add`
tambem. Commit e push, nao.

Alem disso: nunca commit direto na `main`. Sempre branch + Pull Request.

## O que e este servico

Execution Service do Tech Challenge FIAP SOAT, Fase 4: controla a fila da
oficina, a execucao dos servicos de cada OS e o estoque de pecas.

Os outros dois microsservicos:
- OS Service (github.com/Franciscojr08/oficina-os-service): ciclo de vida da
  ordem de servico e orquestrador da Saga. PostgreSQL.
- Billing Service (github.com/rremiao/oficina-billing): orcamento, pagamento
  via Mercado Pago, estorno. PostgreSQL.

## Regras de fronteira

- Nenhum servico acessa o banco de outro. Integracao so por mensageria.
- Saga orquestrada: este servico fala somente com o OS Service (recebe
  comandos, devolve eventos). Nunca fala com o Billing.
- So o OS Service muda o status da ordem de servico.
- Toda mensagem pode chegar duas vezes: todo tratamento precisa ser idempotente.
- Colocar uma OS na fila chega por mensagem (`ComandoHandler`), nunca por REST.

## Arquitetura: simples de proposito

Controller -> Service -> Repository, sem portas e adapters. Pacotes:
`controller`, `service`, `repository`, `model`, `dto`, `messaging`,
`exception`, `config`.

- A classe gravada no Mongo e a propria `model.Execucao` (`@Document`). Nao
  criar classe-espelho nem mapper.
- Regra de mudanca de estado fica em `Execucao`, sem setters.
- A unica interface "de troca" e `messaging.EventoPublisher`, escolhida por
  `oficina.mensageria.tipo` (`log` ou `rabbit`).

## Contrato de mensageria (definido pelo OS Service; nao mudar sozinho)

- Broker: RabbitMQ, o mesmo do OS Service e do Billing. Trocar por SQS so se os
  tres trocarem juntos.
- Comandos chegam do exchange `os.eventos` (topic), routing key = tipo
  (`EnfileirarExecucao`, `CancelarExecucao`), na fila `oficina-execution-comandos`,
  com DLX `oficina-execution-comandos-dlx` e DLQ `oficina-execution-comandos-dlq`.
- Eventos saem no exchange `execucao.eventos` (topic), routing key = tipo, sem
  prefixo. A fila `oficina-os-eventos` do OS Service esta ligada ali.
- Envelope: `messageId`, `tipo`, `sagaId`, `ordemServicoId`, `codigoOrdemServico`,
  `correlationId`, `ocorridoEm`, `versao`, `dados`. Devolver o `sagaId` exatamente
  como chegou; nunca gerar sagaId proprio.
- `ExecucaoAguardandoItens.dados.itensEmFalta` =
  `[{itemId, quantidadeNecessaria, quantidadeDisponivel}]`.
- Copiar `traceparent`, `tracestate` e `newrelic` do comando para o evento de
  resposta (classe `Rastreamento`). Nome no New Relic: `oficina-execucao`.
- Fonte da verdade: `oficina-os-service/docs/alinhamento-contrato-billing-e-execucao.md`
  e `docs/mensageria.md` daquele repositorio.
- Lombok so para `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor`
  e `@Slf4j`. Nada de `@Data` ou `@Setter` nas classes de `model`.

## Armadilhas do Spring Boot 4 ja encontradas

- Starter web chama-se `spring-boot-starter-webmvc`.
- Jackson e o 3: pacote `tools.jackson.databind` (as anotacoes continuam em
  `com.fasterxml.jackson.annotation`). O bean pronto e `JsonMapper`.
- Conexao do Mongo e `spring.mongodb.uri`. `spring.data.mongodb.uri` e ignorado.
- Dependencia sem versao que o BOM do Boot nao gerencia deixa o pom invalido,
  e o IntelliJ para de reconhecer as pastas de codigo.

## Testes

`mvn verify` roda unitarios (JUnit + Mockito), BDD (Cucumber, via
`bdd/ExecucaoBddTest`) e exige 80% de cobertura (JaCoCo).
