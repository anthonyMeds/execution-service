# Roteiro de validacao da Fase 4

Tres niveis, do mais rapido ao mais completo. Comandos em PowerShell, rodados a
partir da pasta `execution-service`.

| Parte | O que prova | Precisa de | Tempo |
| --- | --- | --- | --- |
| **A** | o execution-service sozinho, com RabbitMQ e Mongo de verdade | Docker | ~15 min |
| **B** | os tres servicos conversando, Saga completa e compensacao | Docker e as 3 pastas | ~40 min |
| **C** | deploy no EKS, New Relic e video | Learner Lab ligado | ~1h na primeira vez |

Pre-requisitos: Java 21, Maven, Docker Desktop aberto.

---

## Parte A - execution-service sozinho

### A1. Testes automatizados

```powershell
mvn verify
```

| O que aparece | Requisito |
| --- | --- |
| `Tests run: ..., Failures: 0, Errors: 0` | testes unitarios |
| os 4 cenarios do Cucumber, todos passando | um fluxo completo com BDD |
| `All coverage checks have been met.` | cobertura minima de 80% |

Relatorios: `target\site\jacoco\index.html` (print para `docs/evidencias/`) e
`target\cucumber\relatorio-bdd.html`.

### A2. Subir com Mongo e RabbitMQ

```powershell
docker compose up -d
$env:OFICINA_MENSAGERIA_TIPO = "rabbit"
mvn spring-boot:run
```

Em outro terminal: `Invoke-RestMethod http://localhost:8080/actuator/health` deve
responder `UP`. No painel do RabbitMQ (http://localhost:15672, guest/guest), aba
**Queues**: `oficina-execution-comandos` e `oficina-execution-comandos-dlq`. Aba
**Exchanges** > `os.eventos`: ligacoes `EnfileirarExecucao` e `CancelarExecucao`.

> Se o servico nao subir com `PRECONDITION_FAILED` na fila, sobrou uma fila de um
> teste antigo, com outra configuracao: `docker compose down` e suba de novo.

### A3. Fila de teste para ver os eventos que saem

Os eventos vao para `execucao.eventos`, onde o OS Service escuta. Sem ele no ar,
criamos uma fila que recebe copia de tudo:

```powershell
curl.exe -u guest:guest -H "content-type: application/json" -X PUT "http://localhost:15672/api/queues/%2F/teste-eventos" -d "{}"
curl.exe -u guest:guest -H "content-type: application/json" -X POST "http://localhost:15672/api/bindings/%2F/e/execucao.eventos/q/teste-eventos" -d "@docs/mensagens/vincular-fila-de-teste.json"
```

Para ler, a qualquer momento:

```powershell
curl.exe -s -u guest:guest -H "content-type: application/json" -X POST "http://localhost:15672/api/queues/%2F/teste-eventos/get" -d "@docs/mensagens/ler-mensagens.json"
```

### A4. Caminho feliz

```powershell
# entrada de pecas (sem estoque, toda OS fica aguardando itens)
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/estoque/55/entradas -ContentType "application/json" -Body '{"referencia":"NF-900","quantidade":10}'

# o "OS Service" manda a OS para a oficina (por mensagem, nao HTTP)
curl.exe -u guest:guest -H "content-type: application/json" -X POST "http://localhost:15672/api/exchanges/%2F/os.eventos/publish" -d "@docs/mensagens/enfileirar-os-1042.json"

Invoke-RestMethod http://localhost:8080/execution/v1/execucoes/fila
Invoke-RestMethod http://localhost:8080/execution/v1/estoque/55
```

Esperado: `{"routed":true}`, a OS 1042 na fila com `NA_FILA`, saldo 6. Publique a
mesma mensagem de novo: continua uma execucao so e saldo 6.

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/execucoes/1042/iniciar -ContentType "application/json" -Body '{"mecanico":"Carlos"}'
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/execucoes/1042/servicos/7/finalizar -ContentType "application/json" -Body '{"observacao":"pastilhas trocadas"}'
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/execucoes/1042/servicos/3/finalizar -ContentType "application/json" -Body '{}'
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/execucoes/1042/finalizar
```

Leia a `teste-eventos`: `ExecucaoIniciada`, `ServicoFinalizado` (2x) e
`ExecucaoFinalizada`, todos com o mesmo `sagaId` do comando e um `messageId` novo.

### A5. Falta de peca

Publique `enfileirar-os-2000.json` (pede a peca 77, sem estoque). Esperado: status
`AGUARDANDO_ITENS` e o evento `ExecucaoAguardandoItens` com
`itensEmFalta: [{itemId: 77, quantidadeNecessaria: 2, quantidadeDisponivel: 0}]`.
Tentar iniciar da 409. Registre a entrada da peca 77 e inicie de novo: funciona.

### A6. Compensacao

```powershell
# falha no meio do reparo: devolve as pecas e publica ExecucaoFalhou
Invoke-RestMethod -Method Post http://localhost:8080/execution/v1/execucoes/2000/falhar -ContentType "application/json" -Body '{"motivo":"suspensao comprometida","etapa":"EXECUCAO_SERVICO"}'

# cancelamento vindo do OS Service: publique enfileirar-os-3000.json e depois cancelar-os-3000.json
```

Esperado: OS 2000 `FALHOU` com as pecas de volta no saldo; OS 3000 `CANCELADA`,
sem evento (quem cancelou ja sabe).

### A7. Mensagem com defeito vai para a DLQ

Publique `mensagem-com-defeito.json` em `os.eventos`. O servico continua no ar e a
mensagem aparece em `oficina-execution-comandos-dlq`.

---

## Parte B - os tres servicos na mesma maquina

Um RabbitMQ so, dois PostgreSQL (OS e Billing) e um Mongo. As tres pastas precisam
estar lado a lado (`os-service`, `billing-service`, `execution-service`).

### B1. Subir tudo

Pare antes qualquer `docker compose` de cada repositorio (mesmas portas) e
qualquer servico rodando pela IDE.

```powershell
cd ambiente-local
docker compose --profile apps up -d --build     # a primeira vez demora: compila os tres
docker compose ps                               # todos running/healthy
cd ..
```

> Alternativa mais rapida para depurar: `docker compose up -d` (so a infra) e os
> tres pela IDE, com `SERVER_PORT` 8080 (OS), 8081 (Billing) e 8082 (Execution), e
> `OFICINA_MENSAGERIA_TIPO=rabbit` no execution. Os defaults de banco ja batem.

| Servico | Health |
| --- | --- |
| OS | http://localhost:8080/oficina/v1/actuator/health |
| Billing | http://localhost:8081/billing/v1/actuator/health |
| Execution | http://localhost:8082/actuator/health |

No painel do RabbitMQ, aba **Queues**, as tres filas de entrada:
`oficina-os-eventos`, `oficina-billing-comandos`, `oficina-execution-comandos`.

### B2. Dados iniciais

```powershell
# clientes, veiculos e o usuario admin no banco do OS (depois que o OS subiu e criou as tabelas)
docker cp ..\os-service\scripts\carga-inicial.sql fase4-postgres-os:/tmp/carga-inicial.sql
docker exec fase4-postgres-os psql -U postgres -d os_service -f /tmp/carga-inicial.sql

# login (o mesmo token vale no OS e no Billing: mesmo segredo JWT)
$token = (Invoke-RestMethod -Method Post http://localhost:8080/oficina/v1/auth/login -ContentType "application/json" -Body '{"email":"admin@oficina.com","senha":"admin"}').token
$h = @{ Authorization = "Bearer $token" }

# catalogo com preco, no Billing (o OS nao tem mais catalogo)
$s1 = Invoke-RestMethod -Method Post http://localhost:8081/billing/v1/servicos -Headers $h -ContentType "application/json" -Body '{"nome":"Troca de pastilhas de freio","descricao":"dianteiras","valor":180.00}'
$s2 = Invoke-RestMethod -Method Post http://localhost:8081/billing/v1/servicos -Headers $h -ContentType "application/json" -Body '{"nome":"Alinhamento","descricao":"alinhamento 3D","valor":120.00}'
$i1 = Invoke-RestMethod -Method Post http://localhost:8081/billing/v1/itens -Headers $h -ContentType "application/json" -Body '{"nome":"Pastilha de freio","descricao":"jogo","tipo":"PECA","valorUnitario":45.00,"unidadeMedida":"UN"}'

# estoque no Execution, com o MESMO id de item do catalogo do Billing
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/estoque/$($i1.id)/entradas" -ContentType "application/json" -Body '{"referencia":"NF-1","quantidade":10}'
```

### B3. Abrir a OS e pedir o orcamento

```powershell
$corpo = @{
  clienteId = 1; veiculoId = 1
  descricaoProblema = "barulho ao frear"
  servicos = @(@{ servicoId = $s1.id; nome = $s1.nome }, @{ servicoId = $s2.id; nome = $s2.nome })
  itens    = @(@{ itemId = $i1.id; nome = $i1.nome; quantidade = 4 })
} | ConvertTo-Json -Depth 4
$os = Invoke-RestMethod -Method Post http://localhost:8080/oficina/v1/ordens -Headers $h -ContentType "application/json" -Body $corpo
$id = $os.id

Invoke-RestMethod -Method Patch "http://localhost:8080/oficina/v1/ordens/$id/iniciar-diagnostico" -Headers $h
Invoke-RestMethod -Method Patch "http://localhost:8080/oficina/v1/ordens/$id/solicitar-aprovacao" -Headers $h
Start-Sleep 3
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/status" -Headers $h
```

Esperado: `AGUARDANDO_APROVACAO`. O OS pediu o orcamento ao Billing por mensagem
(`SolicitarOrcamento`) e o Billing respondeu (`OrcamentoGerado`), com os precos do
catalogo dele. Confira: `Invoke-RestMethod "http://localhost:8081/billing/v1/orcamentos/ordem-servico/$id" -Headers $h`.

### B4. Cliente aprova, e o pagamento e simulado

```powershell
Invoke-RestMethod -Method Patch "http://localhost:8080/oficina/v1/ordens/$id/aprovar" -Headers $h
```

O OS manda `CobrarPagamento` ao Billing, que tenta criar a cobranca no Mercado
Pago. **Sem credenciais, a chamada falha** e a mensagem vai para a
`oficina-billing-comandos-dlq` (esperado). A Saga fica aguardando o pagamento.

O script abaixo faz o papel do Mercado Pago aprovando o pagamento: publica o
`PagamentoConfirmado` que o Billing publicaria.

```powershell
.\ambiente-local\simular-billing.ps1 -Evento PagamentoConfirmado -OrdemServicoId $id
Start-Sleep 3
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/status" -Headers $h
Invoke-RestMethod "http://localhost:8082/execution/v1/execucoes/$id"
```

Esperado: a OS em `APROVADA` e, no execution, a mesma OS com `NA_FILA`. O OS
publicou `EnfileirarExecucao` sozinho. **Daqui em diante nada e simulado.**

### B5. A oficina executa

```powershell
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/iniciar" -ContentType "application/json" -Body '{"mecanico":"Carlos"}'
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/status" -Headers $h     # EM_EXECUCAO

Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/servicos/$($s1.id)/finalizar" -ContentType "application/json" -Body '{"observacao":"pastilhas trocadas"}'
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/servicos/$($s2.id)/finalizar" -ContentType "application/json" -Body '{}'
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/finalizar"
Start-Sleep 2
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/status" -Headers $h     # FINALIZADA

Invoke-RestMethod -Method Patch "http://localhost:8080/oficina/v1/ordens/$id/entregar" -Headers $h   # ENTREGUE
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/historico" -Headers $h
```

O historico mostra a OS passando por todos os status, cada mudanca disparada por
um evento do execution-service.

### B6. Saga com falha e compensacao

Abra outra OS repetindo B3 e B4 (guarde o novo `$id`). Com ela `NA_FILA`:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/iniciar" -ContentType "application/json" -Body '{"mecanico":"Carlos"}'
Invoke-RestMethod -Method Post "http://localhost:8082/execution/v1/execucoes/$id/falhar" -ContentType "application/json" -Body '{"motivo":"suspensao comprometida","etapa":"EXECUCAO_SERVICO"}'
```

O execution devolve as pecas e publica `ExecucaoFalhou`. O OS reage mandando
`EstornarPagamento` ao Billing. Como o pagamento foi simulado, o Billing nao o
encontra (DLQ dele, esperado); simule a resposta do estorno:

```powershell
.\ambiente-local\simular-billing.ps1 -Evento PagamentoEstornado -OrdemServicoId $id
Start-Sleep 3
Invoke-RestMethod "http://localhost:8080/oficina/v1/ordens/$id/status" -Headers $h      # CANCELADA
Invoke-RestMethod "http://localhost:8082/execution/v1/estoque/$($i1.id)"                 # pecas de volta
```

### B7. Falta de peca atravessando os tres

Abra uma OS pedindo 50 pastilhas (mais que o estoque). Depois do pagamento
simulado, a OS vai para `AGUARDANDO_ITENS` no OS Service. Registre a entrada de
pecas no execution e inicie: a OS segue para `EM_EXECUCAO`.

### Desligar

```powershell
cd ambiente-local; docker compose --profile apps down; cd ..
```

---

## Parte C - deploy no EKS (video e entrega)

O fluxo local prova a integracao. O EKS e necessario para o que o enunciado pede
no video: **deploy automatizado com validacao de testes** e **monitoramento dos
fluxos distribuidos** (New Relic). E no EKS o pagamento pode ser real: o webhook
do Mercado Pago alcanca o Billing pelo API Gateway.

### C1. Ordem de subida

1. Cluster EKS, metrics-server e New Relic (`oficina-kubernets`, runbook da Fase 3).
2. RDS com dois databases: `os_service` e `billing` (`oficina-database`).
3. **OS Service** pelo pipeline: cria o namespace, o `rabbitmq-secret` e o RabbitMQ.
4. **Billing** pelo pipeline (usa o RabbitMQ do OS).
5. **Execution** pelo pipeline: `.github/workflows/deploy.yml` (ou `k8s/README.md` a mao).
   Tem de subir **antes de qualquer OS ser aprovada**: a fila dele so existe depois que ele sobe.
6. API Gateway (`oficina-lambda`) apontando cada prefixo para o LoadBalancer certo:
   `/oficina/v1` para o OS, `/billing/v1` para o Billing (com o webhook do Mercado
   Pago fora do authorizer) e `/execution/v1` para este servico.

### C2. Secrets do pipeline deste repositorio

| Secret | Valor |
| --- | --- |
| `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` | conta do Docker Hub |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | do Learner Lab (expiram a cada sessao) |
| `EKS_CLUSTER_NAME` | nome do cluster |
| `SONAR_TOKEN`, `SONAR_HOST_URL` | opcionais: sem eles o passo do Sonar e pulado |

### C3. Roteiro do video (parte do execution, ~3 min)

1. Arquitetura: Controller, Service, Repository; Mongo; mensageria.
2. Fluxo: a OS chegando por mensagem, a fila, o mecanico executando, o status mudando no OS.
3. Falha: `falhar` e a OS cancelada depois do estorno (Saga e compensacao).
4. Deploy: um push na `main` disparando CI (testes, cobertura, Sonar) e o deploy no EKS.
5. New Relic: o trace atravessando OS, Execution e de volta (`oficina-execucao`).

---

## O que cada parte prova

| Requisito do enunciado | Onde |
| --- | --- |
| Gerenciar a fila de execucao | A4, B4 |
| Atualizar status durante diagnostico e reparos | B5 (o status muda no OS por eventos daqui) |
| Comunicar finalizacao ao OS Service | B5 (`ExecucaoFinalizada` leva a OS a `FINALIZADA`) |
| Mensageria assincrona | A4, B3 a B7 |
| Nenhum servico acessa o banco de outro | B2: o estoque e por API; o catalogo e do Billing |
| Saga com rollback e compensacao | A6, B6 |
| Testes, BDD e cobertura de 80% | A1 |
| Deploy automatizado em Kubernetes e monitoramento | C |
