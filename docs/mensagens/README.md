# Mensagens de teste

Arquivos prontos para usar com a API de gerenciamento do RabbitMQ local,
fazendo o papel do OS Service enquanto ele nao esta pronto. O passo a passo de
uso esta em [../roteiro-validacao-fase4.md](../roteiro-validacao-fase4.md).

O mesmo comando funciona no PowerShell, no cmd e no bash. No Windows use
`curl.exe`, que ja vem instalado (so `curl` no PowerShell e outra coisa):

```
curl.exe -u oficina:oficina -H "content-type: application/json" -X POST "http://localhost:15672/api/exchanges/%2F/oficina.comandos/publish" -d "@docs/mensagens/enfileirar-os-1042.json"
```

Resposta esperada: `{"routed":true}`. Se vier `false` ou erro 404, o servico
nao esta rodando com `OFICINA_MENSAGERIA_TIPO=rabbit` -- e ele quem cria o
exchange e a fila ao subir.

| Arquivo | O que e |
| --- | --- |
| `enfileirar-os-1042.json` | OS Service mandando a OS 1042 para a oficina (caminho feliz) |
| `enfileirar-os-2000.json` | Idem, OS 2000 (usada no teste de falha) |
| `enfileirar-os-3000.json` | Idem, OS 3000 (usada no teste de cancelamento) |
| `cancelar-os-3000.json` | OS Service cancelando a OS 3000 |
| `mensagem-com-defeito.json` | Mensagem invalida, para ver a dead-letter funcionar |
| `vincular-fila-de-teste.json` | Liga uma fila de teste ao exchange de eventos |
| `ler-mensagens.json` | Parametros para ler mensagens de uma fila sem remove-las |

Tambem da para fazer tudo pelo console: http://localhost:15672 (oficina / oficina).
