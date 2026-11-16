# Deploy da Fase 4 no EKS — passo a passo

Guia para subir os três microsserviços (OS, Execution e Billing) no AWS Learner Lab, do zero.
Foi o caminho que funcionou em 15/11/2026. Os comandos são para **PowerShell** (Windows) e
**AWS CloudShell** (bash, no navegador). Não precisa de Docker nem de WSL na sua máquina.

> Nunca coloque senhas, tokens ou a license key em arquivos do repositório. Elas ficam nos GitHub
> Secrets, nos Secrets do Kubernetes e num arquivo local fora do git.

## Visão geral

| Camada | O quê | Como sobe |
|---|---|---|
| 1. Infra | Cluster EKS `oficina-eks` (2 nós t3.medium) e RDS Postgres com os bancos `os_service` e `billing` | Pipelines do `oficina-kubernetes` e do `oficina-database` |
| 1. Infra | New Relic no cluster | `helm`, pelo PowerShell |
| 2. Serviços | OS (sobe junto o RabbitMQ), Execution (com Mongo próprio), Billing | Imagem gerada no CloudShell, enviada ao ECR e aplicada no cluster |
| 3. Entrada | API Gateway + Lambda autorizadora | `oficina-lambda` (pendente) |

**A ordem importa:** OS antes de todos (é ele que cria o RabbitMQ e o `rabbitmq-secret`) e o
Execution no ar antes de qualquer OS ser aprovada (senão a fila dele não existe e o comando se perde).

## Onde paramos (15/11)

- Pronto: cluster, RDS com os dois bancos, New Relic, **OS** e **Execution** no ar e respondendo `UP`.
- Em andamento: **Billing** — falta gerar a imagem e rodar o deploy (passos 7.3 e 8.3).
- Pendente: teste ponta a ponta, Gateway, pipelines automáticos (ver "Próximos passos").

---

## 1. Ferramentas (uma vez só)

PowerShell, depois feche e abra o PowerShell de novo:

```powershell
winget install -e --id Amazon.AWSCLI
winget install -e --id Kubernetes.kubectl
winget install -e --id Helm.Helm
winget install -e --id GitHub.cli
gh auth login        # GitHub.com → HTTPS → login pelo navegador
```

> Se o PowerShell "congelar" e o título da janela começar com **Selecionar**, aperte **Esc**:
> clicar dentro da janela pausa o comando.

## 2. Credenciais da AWS (a cada Start Lab)

As credenciais do Learner Lab mudam a cada sessão.

1. Learner Lab → **Start Lab** → **AWS Details** → copie o bloco inteiro (de `[default]` até o fim do token).
2. PowerShell:

```powershell
mkdir $env:USERPROFILE\.aws -Force
notepad "$env:USERPROFILE\.aws\credentials"      # Ctrl+A, cole o bloco, Ctrl+S
"[default]`nregion = us-east-1`noutput = json" | Set-Content "$env:USERPROFILE\.aws\config"
aws sts get-caller-identity                       # mostra o Account e um Arn com voclabs
```

> O arquivo tem que começar com `[default]`. Se o bloco vier como `[academy]` ou outro nome, troque.

3. Mande as credenciais para os secrets dos repositórios (os pipelines usam elas):

```powershell
$K = aws configure get aws_access_key_id
$S = aws configure get aws_secret_access_key
$T = aws configure get aws_session_token
$repos = "rremiao/oficina-kubernetes","rremiao/oficina-database","rremiao/oficina-lambda","rremiao/oficina-billing","Franciscojr08/oficina-os-service","anthonyMeds/execution-service"
foreach ($r in $repos) {
  gh secret set AWS_ACCESS_KEY_ID -R $r -b $K
  gh secret set AWS_SECRET_ACCESS_KEY -R $r -b $S
  gh secret set AWS_SESSION_TOKEN -R $r -b $T
  Write-Host "$r -> exit $LASTEXITCODE"     # exit 0 = ok; outro número = sem permissão no repo
}
```

## 3. Infraestrutura (cluster e banco)

### 3.1 Buckets do state do Terraform (uma vez por conta)

O Terraform guarda no S3 a lista do que já criou, para não recriar tudo a cada execução.

```powershell
$ACC = aws sts get-caller-identity --query Account --output text
foreach ($n in "kubernetes","database") {
  $B = "oficina-$n-tfstate-$ACC"
  aws s3api create-bucket --bucket $B --region us-east-1
  aws s3api put-bucket-versioning --bucket $B --versioning-configuration Status=Enabled
  aws s3api put-public-access-block --bucket $B --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
  gh variable set TF_STATE_BUCKET -R "rremiao/oficina-$n" -b $B
}
```

### 3.2 Senha do banco

```powershell
gh secret set DB_PASSWORD -R rremiao/oficina-database
```

Mínimo **8 caracteres**, sem `/`, `@`, `"` nem espaço (o RDS recusa senha curta).

### 3.3 Subir cluster e banco

```powershell
gh workflow run infra-kubernetes.yml -R rremiao/oficina-kubernetes --ref main
gh workflow run infra-database.yml -R rremiao/oficina-database --ref main
gh run list -R rremiao/oficina-kubernetes -L 1      # repita até ✓ (uns 15 min)
```

Quando o cluster estiver ✓, rode o banco **de novo**: é essa segunda rodada que cria os bancos
`os_service` e `billing` (ela precisa do cluster `ACTIVE`).

```powershell
gh workflow run infra-database.yml -R rremiao/oficina-database --ref main
aws eks update-kubeconfig --name oficina-eks --region us-east-1
kubectl get nodes                                   # 2 nós Ready
```

Endereço do banco (vai nas URLs JDBC):

```powershell
aws rds describe-db-instances --db-instance-identifier oficina-db --query "DBInstances[0].Endpoint.Address" --output text
```

## 4. New Relic

1. Conta em one.newrelic.com (região **US**) → seu nome → **API keys** → **Create a key** →
   tipo **Ingest - License** → copie a chave **na hora em que ela aparece completa** (40 caracteres).
   A coluna Value da lista mostra a chave com asteriscos; copiar dali não funciona.
2. PowerShell:

```powershell
helm repo add newrelic https://helm-charts.newrelic.com
helm repo update

$NR = "COLE_A_CHAVE_AQUI"
$NR.Length                                          # tem que dar 40

kubectl create namespace newrelic --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace oficina --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic newrelic-license -n newrelic --from-literal=licenseKey=$NR --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic newrelic-license -n oficina --from-literal=licenseKey=$NR --from-literal=new_relic_license_key=$NR --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install newrelic-bundle newrelic/nri-bundle -n newrelic `
  -f "<pasta>\oficina-kubernets\observability\newrelic-values.yaml" `
  --set global.region=US --timeout 5m

kubectl get pods -n newrelic                        # todos Running
kubectl get crd instrumentations.newrelic.com       # precisa existir antes do deploy dos serviços
```

## 5. Segredos compartilhados (arquivo local)

OS, Billing e o autorizador do Gateway precisam do **mesmo** segredo JWT, e todos usam o **mesmo**
usuário do RabbitMQ. Gere uma vez e guarde **fora do git**:

```powershell
$chars = (48..57)+(65..90)+(97..122)
$JWT = -join ($chars | Get-Random -Count 48 | % {[char]$_})
$RMQ = -join ($chars | Get-Random -Count 20 | % {[char]$_})
"SECURITY_JWT_SECRET=$JWT`nRABBITMQ_USERNAME=oficina`nRABBITMQ_PASSWORD=$RMQ`nDB_PASSWORD=<a senha do passo 3.2>" | Set-Content "$env:USERPROFILE\oficina-segredos.txt"
```

Se já existe (alguém do grupo gerou), peça o arquivo por mensagem privada em vez de gerar outro.

## 6. Secrets dos serviços no cluster (PowerShell)

Precisam existir **antes** do deploy de cada serviço.

```powershell
$s = Get-Content "$env:USERPROFILE\oficina-segredos.txt" | ConvertFrom-StringData
$RDS = aws rds describe-db-instances --db-instance-identifier oficina-db --query "DBInstances[0].Endpoint.Address" --output text

# RabbitMQ (usado pelo OS para criar o broker e pelos outros para conectar)
kubectl create secret generic rabbitmq-secret -n oficina `
  --from-literal=RABBITMQ_DEFAULT_USER="$($s.RABBITMQ_USERNAME)" `
  --from-literal=RABBITMQ_DEFAULT_PASS="$($s.RABBITMQ_PASSWORD)" `
  --dry-run=client -o yaml | kubectl apply -f -

# OS Service
kubectl create secret generic os-service-secret -n oficina `
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://${RDS}:5432/os_service" `
  --from-literal=SPRING_DATASOURCE_USERNAME=oficina_user `
  --from-literal=SPRING_DATASOURCE_PASSWORD="$($s.DB_PASSWORD)" `
  --from-literal=SECURITY_JWT_SECRET="$($s.SECURITY_JWT_SECRET)" `
  --from-literal=SPRING_RABBITMQ_USERNAME="$($s.RABBITMQ_USERNAME)" `
  --from-literal=SPRING_RABBITMQ_PASSWORD="$($s.RABBITMQ_PASSWORD)" `
  --dry-run=client -o yaml | kubectl apply -f -

# Billing (Mercado Pago vazio por enquanto: o pagamento é simulado no teste)
kubectl create secret generic billing-service-secret -n oficina `
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://${RDS}:5432/billing" `
  --from-literal=SPRING_DATASOURCE_USERNAME=oficina_user `
  --from-literal=SPRING_DATASOURCE_PASSWORD="$($s.DB_PASSWORD)" `
  --from-literal=SECURITY_JWT_SECRET="$($s.SECURITY_JWT_SECRET)" `
  --from-literal=SPRING_RABBITMQ_USERNAME="$($s.RABBITMQ_USERNAME)" `
  --from-literal=SPRING_RABBITMQ_PASSWORD="$($s.RABBITMQ_PASSWORD)" `
  --from-literal=MERCADOPAGO_ACCESS_TOKEN= `
  --from-literal=MERCADOPAGO_WEBHOOK_SECRET= `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl get secret -n oficina
```

O Execution não tem secret próprio: usa o `rabbitmq-secret` e o Mongo de dentro do cluster.

## 7. Gerar as imagens (AWS CloudShell)

Console da AWS → ícone **`>_`** no topo. O CloudShell já está logado na conta e tem Docker.

Preparação (a cada sessão do CloudShell):

```bash
mkdir -p ~/bin
[ -x ~/bin/kubectl ] || curl -sLo ~/bin/kubectl "https://dl.k8s.io/release/$(curl -sL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x ~/bin/kubectl
export PATH=$HOME/bin:$PATH
aws eks update-kubeconfig --name oficina-eks --region us-east-1
ACC=$(aws sts get-caller-identity --query Account --output text)
REG=$ACC.dkr.ecr.us-east-1.amazonaws.com
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $REG
```

> **Disco do CloudShell é pequeno.** Rode `docker system prune -af` antes e depois de cada build.
> Sem isso o próximo comando falha com `No space left on device`.

### 7.1 OS Service (repo público)

```bash
docker system prune -af
cd /tmp && rm -rf oficina-os-service
git clone https://github.com/Franciscojr08/oficina-os-service.git && cd oficina-os-service
TAG=$(git rev-parse --short HEAD)
aws ecr create-repository --repository-name oficina-os-service 2>/dev/null
docker build -t $REG/oficina-os-service:$TAG . && docker push $REG/oficina-os-service:$TAG
docker system prune -af
```

### 7.2 Execution Service (repo público)

```bash
docker system prune -af
cd /tmp && rm -rf execution-service
git clone https://github.com/anthonyMeds/execution-service.git && cd execution-service
TAG=$(git rev-parse --short HEAD)
aws ecr create-repository --repository-name oficina-execution 2>/dev/null
docker build -t $REG/oficina-execution:$TAG . && docker push $REG/oficina-execution:$TAG
docker system prune -af
```

### 7.3 Billing Service (repo privado — o código vai pelo S3)

No **PowerShell**, de dentro da pasta local do Billing atualizada (`git pull` na `main`):

```powershell
git log --oneline -1                                     # anote o código do commit (ex.: 87ab002)
git archive --format=zip -o "$env:USERPROFILE\billing.zip" HEAD
$ACC = aws sts get-caller-identity --query Account --output text
aws s3 mb s3://oficina-artefatos-$ACC 2>$null
aws s3 cp "$env:USERPROFILE\billing.zip" s3://oficina-artefatos-$ACC/billing.zip
```

No **CloudShell**:

```bash
docker system prune -af
aws s3 cp s3://oficina-artefatos-$ACC/billing.zip ~/billing.zip
rm -rf /tmp/oficina-billing && mkdir /tmp/oficina-billing && cd /tmp/oficina-billing
unzip -q ~/billing.zip && ls                             # Dockerfile, pom.xml, scripts, k8s, src
TAG=87ab002                                              # o código do commit anotado acima
aws ecr create-repository --repository-name oficina-billing 2>/dev/null
docker build -t $REG/oficina-billing:$TAG . && docker push $REG/oficina-billing:$TAG
docker system prune -af
```

(Se o Rodrigo deixar o repo público, dá para usar `git clone` igual aos outros.)

## 8. Aplicar no cluster (CloudShell)

Sempre nesta ordem.

### 8.1 OS Service (cria também o RabbitMQ)

```bash
cd /tmp/oficina-os-service
bash scripts/deploy-k8s.sh eks oficina $REG/oficina-os-service:$(git rev-parse --short HEAD)
```

Espera terminar com `Deploy concluído`.

### 8.2 Execution Service

```bash
cd /tmp/execution-service
TAG=$(git rev-parse --short HEAD)
kubectl apply -f k8s/06-mongodb.yaml
kubectl apply -f k8s/07-newrelic-instrumentation.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/03-deployment.yaml
kubectl apply -f k8s/04-service-loadbalancer.yaml
kubectl apply -f k8s/05-hpa.yaml
kubectl set image deployment/execution-service execution-service=$REG/oficina-execution:$TAG -n oficina
kubectl rollout status deployment/execution-service -n oficina --timeout=300s
```

Um pod em `ImagePullBackOff` por alguns segundos é normal (é o da imagem padrão, antes do `set image`).

### 8.3 Billing Service

```bash
cd /tmp/oficina-billing
kubectl get secret billing-service-secret -n oficina     # tem que existir (passo 6)
bash scripts/deploy-k8s.sh eks oficina $REG/oficina-billing:$TAG
```

## 9. Conferir

```bash
kubectl get pods -n oficina          # todos 1/1 Running
kubectl get svc -n oficina           # EXTERNAL-IP de cada serviço
kubectl exec -n oficina rabbitmq-0 -- rabbitmqctl list_queues name messages
```

Filas esperadas: `oficina-os-eventos`, `oficina-execution-comandos` (e as `-dlq` de cada uma),
mais a do Billing depois que ele subir.

Health de cada serviço pela internet:

```bash
for s in os-service execution-service-lb; do
  DNS=$(kubectl get svc $s -n oficina -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'); echo "$s -> $DNS"
done
curl -s http://<DNS-do-os-service>/oficina/v1/actuator/health
curl -s http://<DNS-do-execution>/actuator/health
```

Esperado: `{"status":"UP"}`. O DNS de um LoadBalancer novo leva 1 a 2 minutos para responder.

## 10. Problemas que já aconteceram

| Sintoma | Causa | Solução |
|---|---|---|
| `NoCredentials` ou `InvalidClientTokenId` no `aws` | Arquivo sem `[default]` ou credenciais de outra sessão | Passo 2 de novo, com o bloco atual do AWS Details |
| `MasterUserPassword ... shorter than 8 characters` | Senha do banco curta | Passo 3.2 com 8+ caracteres e rodar o workflow de novo |
| Workflow do banco falha com `Cluster status is CREATING` | Rodou antes do cluster ficar pronto | Esperar o cluster ✓ e rodar de novo (correção em PR no `oficina-database`) |
| `helm` falha com `not able to compute the region` | License key em Secret sem região | `--set global.region=US` (correção em PR no `oficina-kubernetes`) |
| Pods `nrk8s-*` em `Error`, log `invalid license` | Chave copiada com asteriscos | Criar chave nova (passo 4), regravar os secrets e `kubectl delete pod -n newrelic --all` |
| `No space left on device` no CloudShell | Imagens antigas ocupando o disco | `docker system prune -af` |
| Execution em `CrashLoopBackOff` com `Connection refused` no Mongo, Mongo `0/1` | Verificação de prontidão do Mongo estourava o tempo | `kubectl patch deployment mongodb -n oficina --type=json -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe","value":{"tcpSocket":{"port":27017},"initialDelaySeconds":5,"periodSeconds":10}}]'` e depois `kubectl delete pod -n oficina -l app.kubernetes.io/name=execution-service` (correção em PR no `execution-service`) |
| `git clone` pede usuário e senha | Repo privado | Usar o caminho do S3 (passo 7.3) |
| Docker Desktop preso em "Engine starting" | WSL antigo ou travado | Não é necessário: gerar as imagens no CloudShell |
| Pipeline: `account is locked due to a billing issue` | Bloqueio de cobrança na conta GitHub do dono do repo | Deploy manual (passos 7 e 8) até o suporte do GitHub desbloquear |
| Pipeline do OS ou Billing falha sem `SONAR_TOKEN` | SonarCloud obrigatório nesses repos | Dono do repo gera o token no SonarCloud e grava o secret |

## 11. Próximos passos

1. **Billing:** terminar os passos 7.3 e 8.3 e conferir (passo 9).
2. **Teste ponta a ponta** pelos LoadBalancers, seguindo a Parte B de
   `execution-service/docs/roteiro-validacao-fase4.md` com os endereços do EKS no lugar de
   `localhost`: login, catálogo no Billing, estoque no Execution, abrir e aprovar a OS, simular o
   pagamento, executar e entregar; depois o caminho de falha com compensação.
3. **Gateway** (`oficina-lambda`, `infra/envs/hml.tfvars`): `backend_lb_dns` = DNS do OS,
   `execution_lb_dns` = DNS do Execution, `newrelic_account_id` = conta do New Relic em uso; rota
   do Billing com o webhook fora do autorizador; a Lambda precisa consultar clientes no banco `os_service`.
4. **Pipelines para o vídeo:** `SONAR_TOKEN` no OS e no Billing, credenciais de teste do Mercado
   Pago no Billing, desbloqueio do Actions da conta do Execution.
5. **Ao terminar:** destruir os recursos (EKS consome crédito do lab mesmo parado).
