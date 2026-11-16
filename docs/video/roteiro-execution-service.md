# Roteiro do vídeo — parte do Execution Service

O vídeo do grupo tem até 15 minutos e precisa mostrar quatro coisas (PDF da Fase 4): o fluxo completo
de uma OS pelos microsserviços, a Saga com tratamento de falhas, o deploy automatizado de pelo menos
um serviço com validação de testes e o monitoramento dos fluxos distribuídos. Este roteiro cobre a
parte do Execution Service em **cerca de 4 minutos** e se encaixa depois da parte do OS Service
(que termina com a OS paga e enviada para a oficina).

## Preparação (antes de gravar)

- Cluster no ar: `kubectl get nodes` com 2 nós `Ready` e `kubectl get pods -n oficina` tudo `1/1`.
  Se o lab foi reiniciado e não houver nós, ver "Retomar o ambiente" no guia de deploy.
- PowerShell com as variáveis `$OS`, `$BI`, `$EX` e o token `$h` (blocos 0 e 2 do teste ponta a ponta).
- Painel do RabbitMQ aberto: `kubectl port-forward -n oficina svc/rabbitmq 15672:15672` e
  http://localhost:15672 (usuário e senha do `oficina-segredos.txt`), na aba **Queues**.
- Postman com a collection `docs/postman/execution-service.postman_collection.json`, variável de
  URL apontando para o LoadBalancer do execution (ou o API Gateway).
- Abas abertas: GitHub Actions de `oficina-soat-pos-fiap/execution-service`, New Relic
  (APM `oficina-execucao` e Distributed tracing), README do repositório (diagrama).
- Três OS preparadas pela parte do OS Service:
  1. **OS A** paga e na fila (`NA_FILA`) — caminho feliz.
  2. **OS B** pedindo mais peças do que há em estoque — falta de peça.
  3. **OS C** paga e na fila — falha com compensação.
- Um PR pequeno aberto no execution (ex.: ajuste no README) para mostrar o CI.

## Roteiro

| Tempo | O que mostrar | O que falar |
| --- | --- | --- |
| 0:00–0:30 | README: diagrama e pacotes | O Execution Service é dono da fila da oficina, do estoque de peças e do tempo de cada serviço. Camadas Controller → Service → Repository, banco próprio MongoDB (documento da execução com serviços e peças embutidos, sem joins). Não acessa o banco de nenhum outro serviço: recebe comandos e devolve eventos pelo RabbitMQ. |
| 0:30–1:00 | Painel do RabbitMQ: fila `oficina-execution-comandos` e a `-dlq`; `GET /execucoes/fila` | A OS chegou aqui por mensagem, não por HTTP: o OS Service publicou `EnfileirarExecucao` no exchange `os.eventos`. A fila tem DLQ: mensagem com defeito não trava a fila. O `sagaId` da mensagem é devolvido igual em toda resposta, e é com ele que o OS sabe a que ciclo da Saga o evento pertence. |
| 1:00–2:00 | `iniciar` (mecânico), `GET /ordens/{id}/status` no OS, finalizar os serviços, `finalizar`, `GET /estoque/{itemId}` | Ao iniciar, a OS muda para "Em execução" no OS Service, sem nenhuma chamada HTTP entre os dois: o execution publicou `ExecucaoIniciada`. Cada serviço tem início, fim e duração registrados (`ServicoFinalizado`). No fim, `ExecucaoFinalizada` leva a OS a "Finalizada", e as peças reservadas viram baixa definitiva no estoque. Relatório: `GET /relatorios/tempo-medio-servicos`. |
| 2:00–2:40 | OS B: `GET /execucoes/{id}` em `AGUARDANDO_ITENS`; entrada de estoque; `iniciar` | Faltou peça: o execution não quebra a Saga, ele avisa o OS (`ExecucaoAguardandoItens`, com quanto falta de cada item) e espera. Quando a peça chega (`POST /estoque/{itemId}/entradas`), o mecânico inicia e a reserva é feita nessa hora. |
| 2:40–3:20 | OS C: `iniciar` e `falhar`; status no OS; estoque de volta | Falha no meio do reparo: o execution devolve as peças ao estoque (compensação local) e publica `ExecucaoFalhou`. O OS Service, que orquestra a Saga, pede o estorno ao Billing e só depois de `PagamentoEstornado` a OS vai para "Cancelada". Cada serviço desfaz só o que é dele. |
| 3:20–3:50 | GitHub Actions: CI do PR (testes, BDD, JaCoCo) e o Deploy após o merge na main | Todo PR roda testes unitários, os cenários BDD do Cucumber e a cobertura mínima de 80% (o build quebra abaixo disso). O merge na main gera a imagem no ECR e aplica os manifests no EKS, com rollout sem queda. |
| 3:50–4:20 | New Relic: APM `oficina-execucao` e um trace distribuído | O contexto do trace (`traceparent`, `newrelic`) viaja nos cabeçalhos da mensagem: a requisição no OS, o comando consumido aqui e o evento de volta aparecem no mesmo trace. |

## Comandos de apoio (PowerShell)

```powershell
# fila da oficina
Invoke-RestMethod "$EX/execucoes/fila"

# caminho feliz (OS A = $a)
Invoke-RestMethod -Method Post "$EX/execucoes/$a/iniciar" -ContentType "application/json" -Body '{"mecanico":"Carlos"}'
Invoke-RestMethod "$OS/ordens/$a/status" -Headers $h                       # EM_EXECUCAO
Invoke-RestMethod -Method Post "$EX/execucoes/$a/servicos/1/finalizar" -ContentType "application/json" -Body '{"observacao":"pastilhas trocadas"}'
Invoke-RestMethod -Method Post "$EX/execucoes/$a/servicos/2/finalizar" -ContentType "application/json" -Body '{}'
Invoke-RestMethod -Method Post "$EX/execucoes/$a/finalizar"
Invoke-RestMethod "$OS/ordens/$a/status" -Headers $h                       # FINALIZADA
Invoke-RestMethod "$EX/estoque/1"
Invoke-RestMethod "$EX/relatorios/tempo-medio-servicos"

# falta de peça (OS B = $b)
Invoke-RestMethod "$EX/execucoes/$b"                                       # AGUARDANDO_ITENS
Invoke-RestMethod -Method Post "$EX/estoque/1/entradas" -ContentType "application/json" -Body '{"referencia":"NF-2","quantidade":20}'
Invoke-RestMethod -Method Post "$EX/execucoes/$b/iniciar" -ContentType "application/json" -Body '{"mecanico":"Ana"}'

# falha e compensação (OS C = $c)
Invoke-RestMethod -Method Post "$EX/execucoes/$c/iniciar" -ContentType "application/json" -Body '{"mecanico":"Carlos"}'
Invoke-RestMethod -Method Post "$EX/execucoes/$c/falhar" -ContentType "application/json" -Body '{"motivo":"suspensao comprometida","etapa":"EXECUCAO_SERVICO"}'
.\ambiente-local\simular-billing-eks.ps1 -Evento PagamentoEstornado -OrdemServicoId $c   # sem Mercado Pago real
Invoke-RestMethod "$OS/ordens/$c/status" -Headers $h                       # CANCELADA
```

> Com as credenciais de teste do Mercado Pago no Billing, o pagamento e o estorno acontecem de
> verdade e os passos com `simular-billing-eks.ps1` deixam de ser necessários.

## Frases curtas para não esquecer

- "A OS chega por mensagem e o status volta por evento: nenhum serviço chama o outro."
- "Faltou peça não é erro: é uma etapa da Saga."
- "Cada serviço desfaz só o que é dele: aqui, devolver as peças."
- "Uma requisição, três serviços, um trace."
