# execution-service

Microsservico de execucao de ordens de servico da oficina.
Tech Challenge FIAP SOAT, Fase 4.

> **Chegou agora no projeto?** O guia de onboarding explica o negocio, o fluxo
> passo a passo e cada classe: https://claude.ai/artifact/XpPoSstL5vzW3YX8dVs9ea
>
> Para testar tudo na sua maquina: [docs/roteiro-validacao-fase4.md](docs/roteiro-validacao-fase4.md)

## O que ele faz

Controla o trabalho dentro da oficina: a fila de execucao, o inicio e o fim de
cada servico, e o consumo de pecas do estoque. Entra em cena quando o pagamento
e confirmado e sai quando a ordem de servico esta pronta para entrega.

Nao guarda preco (isso e do Billing) e nao muda o status da ordem de servico
(isso e do OS Service): ele avisa o que aconteceu, por evento.

## Arquitetura

Tres camadas, como nos outros projetos do time:

```
Controller  ->  Service  ->  Repository  ->  MongoDB
                   |
                   +-> EventoPublisher -> RabbitMQ -> OS Service
```

```
br.com.oficina.execution
├── controller/   Endpoints REST e o tratador de erros
├── service/      As regras: ExecucaoService, EstoqueService, RelatorioService
├── repository/   Interfaces MongoRepository (o Spring gera a implementacao)
├── model/        Execucao e MovimentacaoEstoque: o que e gravado no Mongo
├── dto/          Formato das requisicoes e respostas da API
├── messaging/    Entrada (comandos) e saida (eventos) por mensageria
├── exception/    Excecoes de negocio
└── config/       Filas do RabbitMQ e o filtro de correlationId
```

A regra de mudanca de estado (quem pode virar o que) fica na propria classe
`Execucao`, que nao tem setter: o status so muda por `iniciar()`, `finalizar()`,
`cancelar()`, `falhar()`.

## Saga Pattern: estrategia escolhida e por que

**Saga ORQUESTRADA**, com o OS Service como orquestrador.

Este servico nunca fala com o Billing. Ele recebe **comandos** do OS Service e
devolve **eventos** para o OS Service, que decide o proximo passo:

```
OS Service  --EnfileirarExecucao-->       Execution
Execution   --ExecucaoAguardandoItens-->  OS Service   (faltou peca)
Execution   --ExecucaoIniciada-->         OS Service   (OS vai para EM_EXECUCAO)
Execution   --ServicoFinalizado-->        OS Service
Execution   --ExecucaoFinalizada-->       OS Service   (OS vai para FINALIZADA)
Execution   --ExecucaoFalhou-->           OS Service   (OS Service manda o Billing estornar)
OS Service  --CancelarExecucao-->         Execution    (compensacao vinda de fora)
```

Por que orquestrada e nao coreografada:

- O fluxo inteiro fica escrito em um lugar (o orquestrador), em vez de
  espalhado por tres repositorios reagindo uns aos outros.
- Para depurar, o orquestrador sabe em que passo cada OS esta.
- So o orquestrador decide desfazer; os outros servicos nao precisam adivinhar.

O custo: o OS Service vira ponto central e muda sempre que o fluxo muda.
Aceitavel para tres servicos.

### Compensacoes deste servico

Nao existe `rollback` entre microsservicos: o pagamento ja foi para o Mercado
Pago e nenhum banco desfaz isso. Cada passo tem um passo que o compensa.

| Passo | O que o desfaz |
| --- | --- |
| Reserva de peca | movimentacao de `ESTORNO`, que devolve o saldo |
| Execucao que falhou | evento `ExecucaoFalhou`, que leva ao estorno do pagamento |
| Execucao na fila | comando `CancelarExecucao`, que cancela e devolve as pecas |

### A mesma mensagem pode chegar duas vezes

Filas entregam "pelo menos uma vez". Tres protecoes:

- `enfileirar` procura a execucao pelo `sagaId` antes de criar outra;
- `cancelar` em execucao ja cancelada nao faz nada, e nao da erro;
- cada movimentacao de estoque tem indice unico em `(referencia, itemId, tipo)`:
  o proprio banco recusa a duplicata.

## Decisoes

**MongoDB.** O trabalho exige um banco NoSQL no conjunto, e este e o servico
onde ele encaixa: uma execucao e naturalmente um documento, com a lista de
servicos, as pecas e o historico dentro dele. Em SQL seriam quatro tabelas.

**Estoque como soma de movimentacoes.** O saldo nao e um campo: e a soma das
entradas, reservas e estornos. Evita atualizar o mesmo registro a partir de
varias OS e deixa o historico completo de graca.

**RabbitMQ, com a porta aberta para SNS + SQS.** O Rabbit sobe no
`docker compose`, entao o fluxo dos tres servicos pode ser testado sem AWS.
A tecnologia fica atras da propriedade `oficina.mensageria.tipo`:

| Valor | O que acontece |
| --- | --- |
| `log` (padrao) | nao usa broker; os eventos so aparecem no log |
| `rabbit` | publica e consome no RabbitMQ |

Para trocar por SNS + SQS: criar `SqsEventoPublisher` (implementando
`EventoPublisher`) e um listener que chame `ComandoHandler.tratar(json)`, os
dois com `havingValue = "sqs"`. Nada fora do pacote `messaging` muda.

**Mensagem com erro vai para a dead-letter.** Se o processamento de um comando
falha, a mensagem nao volta para a fila (daria o mesmo erro para sempre): vai
para `oficina-execution-comandos-dlq`, onde fica guardada para investigacao.

## Rodando local

Pre-requisitos: Java 21, Maven e Docker.

```bash
docker compose up -d        # Mongo e RabbitMQ
mvn spring-boot:run         # http://localhost:8080
```

Para ligar a mensageria de verdade (PowerShell):

```powershell
$env:OFICINA_MENSAGERIA_TIPO = "rabbit"
mvn spring-boot:run
```

O passo a passo completo de teste, com as mensagens prontas, esta em
[docs/roteiro-validacao-fase4.md](docs/roteiro-validacao-fase4.md).

## API

| Metodo | Rota | O que faz |
| --- | --- | --- |
| GET | `/execution/v1/execucoes/fila` | Fila da oficina, mais antiga primeiro |
| GET | `/execution/v1/execucoes/{ordemServicoId}` | Estado da execucao de uma OS |
| POST | `/execution/v1/execucoes/{ordemServicoId}/iniciar` | Mecanico pega a OS da fila |
| POST | `/execution/v1/execucoes/{ordemServicoId}/servicos/{servicoId}/finalizar` | Conclui um servico |
| POST | `/execution/v1/execucoes/{ordemServicoId}/finalizar` | Fecha a execucao |
| POST | `/execution/v1/execucoes/{ordemServicoId}/falhar` | Falha: dispara a compensacao |
| GET | `/execution/v1/estoque/{itemId}` | Saldo da peca |
| POST | `/execution/v1/estoque/{itemId}/entradas` | Entrada de peca do fornecedor |
| GET | `/execution/v1/relatorios/tempo-medio-os` | Tempo medio de execucao de OS |
| GET | `/execution/v1/relatorios/tempo-medio-servicos` | Tempo medio por servico |
| GET | `/actuator/health` | Saude (os probes do Kubernetes usam `/liveness` e `/readiness`) |

Colocar uma OS na fila **nao tem endpoint**: chega por mensagem, do OS Service.

Collection Postman em [docs/postman/](docs/postman/).

## Testes e cobertura

```bash
mvn verify
```

Roda os testes unitarios, os cenarios BDD e confere a cobertura.
**O build quebra abaixo de 80%**, o minimo exigido pela Fase 4.

| Tipo | Onde | O que cobre |
| --- | --- | --- |
| Unitario de modelo | `model/ExecucaoTest` | regras de mudanca de estado |
| Unitario de service | `service/*Test` | regras, com repositorio e fila de mentira (Mockito) |
| Unitario de mensageria | `messaging/ComandoHandlerTest` | leitura do JSON dos comandos |
| Unitario de controller | `controller/ControllersTest` | conversao para resposta e status HTTP |
| BDD (Cucumber) | `src/test/resources/features/execucao.feature` | quatro fluxos completos, em portugues |

Relatorios gerados: cobertura em `target/site/jacoco/index.html`, BDD em
`target/cucumber/relatorio-bdd.html`. O CI publica os dois como artefato.

### Evidencia de cobertura

_Pendente: rodar `mvn verify` e salvar o print em `docs/evidencias/cobertura.png`
(instrucoes em [docs/evidencias/](docs/evidencias/))._

## Deploy

Manifests em [k8s/](k8s/). O pipeline `.github/workflows/deploy.yml` gera a
imagem, publica no Docker Hub e aplica no EKS depois que o CI passa na `main`.
A observabilidade e a mesma da Fase 3: o Deployment tem a anotacao que faz o
operador do New Relic injetar o agente.

## O que falta

- [ ] Conta no MongoDB Atlas e Secret com a connection string
- [ ] RabbitMQ no cluster (branch `feat/rabbitmq-mensageria` do `oficina-kubernets`)
- [ ] Rota no API Gateway (branch `feat/rota-execution-service` do `oficina-lambda`)
- [ ] Print da cobertura em `docs/evidencias/`
- [ ] Proteger a branch `main` (PR obrigatorio e CI como checagem)
- [ ] Teste de ponta a ponta com o OS Service e o Billing
