<#
Versao do simular-billing.ps1 para o cluster EKS: publica no RabbitMQ do cluster a
resposta que o Billing daria depois que o Mercado Pago aprovasse (ou estornasse) o
pagamento.

Para que serve: sem credenciais do Mercado Pago, o CobrarPagamento vai para a DLQ do
Billing e a Saga fica esperando. Este script destrava esse passo; daqui em diante o
fluxo e 100% real.

Pre-requisitos:
  - kubectl apontando para o cluster (aws eks update-kubeconfig --name oficina-eks)
  - %USERPROFILE%\oficina-segredos.txt com RABBITMQ_USERNAME, RABBITMQ_PASSWORD e DB_PASSWORD

Uso (PowerShell, a partir da pasta execution-service):
  .\ambiente-local\simular-billing-eks.ps1 -Evento PagamentoConfirmado -OrdemServicoId 1
  .\ambiente-local\simular-billing-eks.ps1 -Evento PagamentoEstornado  -OrdemServicoId 1
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("PagamentoConfirmado", "PagamentoEstornado", "PagamentoRecusado")]
    [string] $Evento,

    [Parameter(Mandatory = $true)]
    [long] $OrdemServicoId,

    [long] $PagamentoId = (900000 + $OrdemServicoId),

    [string] $ArquivoSegredos = "$env:USERPROFILE\oficina-segredos.txt"
)

$ErrorActionPreference = "Stop"
$s = Get-Content $ArquivoSegredos | ConvertFrom-StringData
$rds = (aws rds describe-db-instances --db-instance-identifier oficina-db `
        --query "DBInstances[0].Endpoint.Address" --output text).Trim()

# O sagaId do ciclo atual da OS: o OS Service recusa evento de outro ciclo.
# O RDS e privado, entao a consulta roda num pod descartavel dentro do cluster.
$consulta = "select saga_id from saga_ordem_servico where ordem_servico_id = $OrdemServicoId"
# Sem "-i": o attach do kubectl run perde a saida quando o psql termina rapido demais.
# O pod roda ate o fim, a saida vem do log e o pod e apagado em seguida.
$pod = "psql-saga-$(Get-Random -Maximum 99999)"
kubectl run $pod -n oficina --restart=Never --image=postgres:16-alpine `
    --env="PGPASSWORD=$($s.DB_PASSWORD)" --command -- `
    psql -h $rds -U oficina_user -d os_service -tAc $consulta | Out-Null
try {
    kubectl wait -n oficina "--for=jsonpath={.status.phase}=Succeeded" "pod/$pod" --timeout=120s | Out-Null
    $sagaId = (kubectl logs -n oficina $pod | Out-String).Trim()
} finally {
    kubectl delete pod -n oficina $pod --ignore-not-found --wait=false | Out-Null
}
if ($sagaId -notmatch '^[0-9a-fA-F-]{36}$') {
    throw "A OS $OrdemServicoId ainda nao tem Saga (resposta: '$sagaId'). Rode antes o PATCH /ordens/$OrdemServicoId/solicitar-aprovacao."
}

if ($Evento -eq "PagamentoRecusado") {
    $dados = @{ motivo = "recusado na simulacao" }
} else {
    $dados = @{ pagamentoId = $PagamentoId }
}

$messageId = [guid]::NewGuid().ToString()
$envelope = [ordered]@{
    messageId          = $messageId
    tipo               = $Evento
    sagaId             = $sagaId
    ordemServicoId     = $OrdemServicoId
    codigoOrdemServico = $null
    correlationId      = "simulacao-billing-$OrdemServicoId"
    ocorridoEm         = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    versao             = 1
    dados              = $dados
}

# Mesmo formato do Billing: exchange billing.eventos, routing key = tipo.
$corpo = [ordered]@{
    properties       = @{ content_type = "application/json"; message_id = $messageId; type = $Evento }
    routing_key      = $Evento
    payload          = ($envelope | ConvertTo-Json -Depth 5 -Compress)
    payload_encoding = "string"
} | ConvertTo-Json -Depth 5

# Painel/API do RabbitMQ do cluster, aberto so durante o script.
$pf = Start-Process kubectl -ArgumentList "port-forward -n oficina svc/rabbitmq 15672:15672" `
        -WindowStyle Hidden -PassThru
$arquivo = New-TemporaryFile
try {
    Start-Sleep -Seconds 4
    [System.IO.File]::WriteAllText($arquivo.FullName, $corpo)
    # curl.exe em vez de Invoke-RestMethod: o PowerShell 5 troca o %2F da URL por "/".
    $resposta = curl.exe -s -u "$($s.RABBITMQ_USERNAME):$($s.RABBITMQ_PASSWORD)" `
        -H "content-type: application/json" -X POST `
        "http://localhost:15672/api/exchanges/%2F/billing.eventos/publish" -d "@$($arquivo.FullName)"
} finally {
    Remove-Item $arquivo -ErrorAction SilentlyContinue
    Stop-Process -Id $pf.Id -ErrorAction SilentlyContinue
}

if ($resposta -match '"routed":true') {
    Write-Host "$Evento publicado para a OS $OrdemServicoId (saga $sagaId)."
} else {
    Write-Warning "O RabbitMQ nao entregou a mensagem a nenhuma fila. Resposta: $resposta"
}
