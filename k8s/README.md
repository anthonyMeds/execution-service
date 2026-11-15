# Manifests do execution-service

Aplicar na ordem, com o cluster da Fase 3 ja de pe (namespace `oficina` criado
pelo repositorio `oficina-kubernets`):

```bash
cp k8s/02-secret.yaml.example k8s/02-secret.yaml
# edite o MONGODB_URI com a string do Atlas

kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml
kubectl apply -f k8s/03-deployment.yaml
kubectl apply -f k8s/04-service-loadbalancer.yaml
kubectl apply -f k8s/05-hpa.yaml

kubectl get pods -n oficina -w
kubectl get svc execution-service-lb -n oficina
```

O DNS do LoadBalancer que sair do ultimo comando precisa ser configurado no
`oficina-lambda`, como a variavel de backend deste servico no API Gateway.

O HPA depende do metrics-server, que ja e instalado pelo runbook do
`oficina-kubernets`.
