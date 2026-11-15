<#
Faz o papel do Mercado Pago no ambiente local: publica no RabbitMQ a resposta
que o Billing daria depois que o pagamento fosse aprovado (ou estornado).

Por que existe: o Billing so confirma pagamento quando o Mercado Pago chama o
webhook dele, o que exige credenciais de teste e uma URL publica. Localmente nao
ha nenhum dos dois, entao a Saga para esperando o pagamento. Este script
destrava esse passo; daqui em diante o fluxo volta a ser 100% real.

Uso (PowerShell, a partir da pasta execution-service):
  .\ambiente-local\simular-billing.ps1 -Evento PagamentoConfirmado -OrdemServicoId 1
  .\ambiente-local\simular-billing.ps1 -Evento PagamentoEstornado  -OrdemServicoId 1

Precisa do ambiente-local/docker-compose.yml no ar (le o sagaId no banco do OS).
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("PagamentoConfirmado", "PagamentoEstornado", "PagamentoRecusado")]
    [string] $Evento,

    [Parameter(Mandatory = $true)]
    [long] $OrdemServicoId,

    # O OS so exige que seja numerico; o mesmo id serve para confirmar e estornar.
    [long] $PagamentoId = (900000 + $OrdemServicoId)
)

$ErrorActionPreference = "Stop"

# O sagaId do ciclo atual da OS: o OS Service recusa evento de outro ciclo.
$consulta = "select saga_id from saga_ordem_servico where ordem_servico_id = $OrdemServicoId"
$sagaId = (docker exec fase4-postgres-os psql -U postgres -d os_service -tAc $consulta | Out-String).Trim()
if (-not $sagaId) {
    throw "A OS $OrdemServicoId ainda nao tem Saga. Rode antes o PATCH /ordens/$OrdemServicoId/solicitar-aprovacao."
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

# curl.exe em vez de Invoke-RestMethod: o PowerShell 5 troca o %2F da URL por "/",
# e a API do RabbitMQ precisa dele (e o nome do virtual host "/").
$arquivo = New-TemporaryFile
try {
    [System.IO.File]::WriteAllText($arquivo.FullName, $corpo)
    $resposta = curl.exe -s -u guest:guest -H "content-type: application/json" -X POST `
        "http://localhost:15672/api/exchanges/%2F/billing.eventos/publish" -d "@$($arquivo.FullName)"
} finally {
    Remove-Item $arquivo -ErrorAction SilentlyContinue
}

if ($resposta -match '"routed":true') {
    Write-Host "$Evento publicado para a OS $OrdemServicoId (saga $sagaId)."
} else {
    Write-Warning "O RabbitMQ nao entregou a mensagem a nenhuma fila. O OS Service esta no ar? Resposta: $resposta"
}
