# Manifests do execution-service

O servico roda no namespace `oficina`, junto com o OS Service e o Billing, e usa
o **RabbitMQ que o OS Service implanta** (StatefulSet e Service `rabbitmq`, com as
credenciais no `rabbitmq-secret`). Por isso a ordem importa:

1. **OS Service primeiro** (pipeline dele): cria o namespace, o `rabbitmq-secret` e o RabbitMQ.
2. **Execution antes de qualquer OS ser aprovada.** A fila `oficina-execution-comandos`
   e criada quando este servico sobe; se o OS publicar um `EnfileirarExecucao` antes
   disso, o RabbitMQ descarta a mensagem porque nao ha fila ligada.

| Arquivo | O que e |
| --- | --- |
| `01-configmap.yaml` | mensageria, endereco do Mongo e New Relic (`oficina-execucao`) |
| `02-secret.yaml.example` | opcional: so para usar o MongoDB Atlas |
| `03-deployment.yaml` | o servico; usuario e senha do RabbitMQ vem do `rabbitmq-secret` |
| `04-service-loadbalancer.yaml` | exposto para o API Gateway (rota `/execution/v1`) |
| `05-hpa.yaml` | escala de 1 a 3 pods por CPU (precisa do metrics-server) |
| `06-mongodb.yaml` | MongoDB dentro do cluster, sem volume persistente |
| `07-newrelic-instrumentation.yaml` | injecao do agent Java do New Relic (so com o New Relic instalado no cluster) |

## Aplicar a mao

```bash
kubectl apply -f k8s/06-mongodb.yaml
kubectl apply -f k8s/07-newrelic-instrumentation.yaml   # antes do deployment
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/03-deployment.yaml
kubectl apply -f k8s/04-service-loadbalancer.yaml
kubectl apply -f k8s/05-hpa.yaml

kubectl rollout status deployment/execution-service -n oficina
kubectl get svc execution-service-lb -n oficina    # DNS para o API Gateway
```

O pipeline `.github/workflows/deploy.yml` faz exatamente isso, com a imagem nova do
ECR ja no apply do deployment. A mao, troque a imagem antes do rollout:

```bash
kubectl set image deployment/execution-service \
  execution-service=<conta>.dkr.ecr.us-east-1.amazonaws.com/oficina-execution:<commit> -n oficina
```

## Conferir que a fila foi criada

```bash
kubectl port-forward svc/rabbitmq 15672:15672 -n oficina
```

Em `http://localhost:15672`, aba **Queues**: devem existir `oficina-execution-comandos`
e `oficina-execution-comandos-dlq`. Na aba **Exchanges**, `os.eventos` deve listar a
ligacao para a fila com as routing keys `EnfileirarExecucao` e `CancelarExecucao`.
