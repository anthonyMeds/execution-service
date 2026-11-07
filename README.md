# execution-service

Microsservico de execucao de ordens de servico da oficina.
Tech Challenge FIAP SOAT, Fase 4.

## O que ele faz

E o dono de tudo que tem relogio no fluxo da oficina: a fila de execucao, o
inicio e o fim de cada servico, a duracao, e o consumo de pecas do estoque.

Entra em cena quando o pagamento e confirmado e sai quando a ordem de servico
esta pronta para entrega.

## Onde ele fica na Saga

```
OS Service  --EnfileirarExecucao-->  Execution
Execution   --ExecucaoAguardandoItens-->  OS Service   (se faltar peca)
Execution   --ExecucaoIniciada-->         OS Service   (OS vai para EM_EXECUCAO)
Execution   --ServicoFinalizado-->        OS Service
Execution   --ExecucaoFinalizada-->       OS Service   (OS vai para FINALIZADA)
Execution   --ExecucaoFalhou-->           OS Service   (dispara o estorno)
```

O `ExecucaoFalhou` e a compensacao mais importante do trabalho: faz o OS Service
mandar o Billing estornar o pagamento no Mercado Pago.

## Decisoes de arquitetura

**MongoDB, e nao PostgreSQL.** O documento natural e "a execucao de uma OS":
lista de servicos com estado e timestamps, pecas consumidas e historico, tudo
lido junto, que e exatamente como a tela de acompanhamento consulta. Em SQL
seriam quatro tabelas com join.

**Estoque append-only.** O saldo nao e um campo que se escreve por cima: e a
soma das movimentacoes. Isso resolve o ponto fraco de saldo em banco de
documentos sem depender de transacao multi-documento, e da idempotencia de graca
-- cada movimentacao carrega o `sagaId`, e ha indice unico sobre
`(sagaId, itemId, tipo)`.

**Clean Architecture.** `core/` nao conhece Mongo, Spring Web nem mensageria.
E o que permite testar o dominio inteiro sem subir banco, e o que vai permitir
plugar SQS ou RabbitMQ depois sem tocar em regra de negocio.

**Sem Lombok.** Java 21 com records cobre o caso e evita a classe de problemas
de annotation processor que ja custou tempo no repositorio do billing.

## Estrutura

```
core/
  domain/     Execucao (raiz do agregado), ServicoExecucao, MovimentacaoEstoque
  port/out/   O que o dominio precisa do mundo externo
  usecase/    Um caso de uso por acao
  evento/     Envelope e tipos de mensagem, iguais ao contrato do time
adapter/
  in/web/     Controller REST, tratador de erros, CorrelationIdFilter
  out/mongo/  Documentos, repositorios e o mapper para o dominio
  out/evento/ Publicador de eventos (provisorio: so loga)
```

## Rodando local

```bash
docker compose up -d        # sobe o Mongo
mvn spring-boot:run      # sobe o servico em http://localhost:8080
```

Health check: `GET /actuator/health`

Para gerar o Maven Wrapper (`mvnw`), como no repositorio do billing:
`mvn -N wrapper:wrapper`

## Endpoints

| Metodo | Rota | O que faz |
| --- | --- | --- |
| GET | `/execution/v1/execucoes/fila` | Fila de espera, mais antiga primeiro |
| GET | `/execution/v1/execucoes/{ordemServicoId}` | Estado da execucao de uma OS |
| POST | `/execution/v1/execucoes/{id}/iniciar` | Mecanico pega a OS da fila |
| POST | `/execution/v1/execucoes/{id}/servicos/{servicoId}/finalizar` | Conclui um servico |
| POST | `/execution/v1/execucoes/{id}/finalizar` | Fecha a execucao |
| POST | `/execution/v1/execucoes/{id}/falhar` | Falha, dispara o estorno |

`EnfileirarExecucao` nao tem endpoint de proposito: chega por mensagem.

## Testes

```bash
mvn verify               # testes + relatorio de cobertura em target/site/jacoco
```

## O que falta

- [ ] Adapter de mensageria (depende da decisao entre SNS+SQS e RabbitMQ)
- [ ] Listener dos comandos `EnfileirarExecucao` e `CancelarExecucao`
- [ ] Testes BDD com Cucumber
- [ ] Testcontainers nos testes de integracao
- [ ] Swagger
- [ ] Endpoint de relatorio (tempo medio de OS e por servico)
- [ ] Manifests do Kubernetes
- [ ] Subir `jacoco.cobertura.minima` para 0.80
