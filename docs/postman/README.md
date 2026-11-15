# Collection Postman

Importe `execution-service.postman_collection.json` no Postman.

## Variaveis

| Variavel | Para que serve |
| --- | --- |
| `baseUrl` | `http://localhost:8080` no local, ou a URL do API Gateway |
| `token` | JWT emitido pelo `POST /auth/token` do `oficina-lambda` |
| `ordemServicoId`, `servicoId`, `itemId` | facilitam repetir as chamadas |

## Roteiro para demonstrar o fluxo

1. **Registrar entrada de peca** com quantidade 10. Sem isto o saldo e zero e
   toda execucao cai em `AGUARDANDO_ITENS`.
2. Publicar o comando `EnfileirarExecucao` na fila (nao ha endpoint para isto
   de proposito; veja o README da raiz).
3. **Consultar fila** e conferir que a OS aparece.
4. **Iniciar execucao** com um mecanico.
5. **Finalizar um servico** para cada servico da OS.
6. **Finalizar a execucao**.
7. **Tempo medio de OS** para ver o numero aparecer.

Para demonstrar a compensacao, repita ate o passo 4 e chame
**Falhar a execucao** em vez de finalizar: o saldo da peca volta ao que era.
