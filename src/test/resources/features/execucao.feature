# language: pt
Funcionalidade: Execucao de ordem de servico na oficina
  Depois que o cliente paga o orcamento, a OS precisa entrar na fila da oficina,
  ter as pecas reservadas, ser executada servico por servico, e o OS Service
  precisa ser avisado do que aconteceu.

  Cenario: Fluxo completo, do pagamento confirmado ate a OS finalizada
    Dado que existem 10 unidades da peca 55 em estoque
    Quando chega o comando para enfileirar a OS 1042 com os servicos 7 e 3 e 4 unidades da peca 55
    Entao a execucao da OS 1042 fica com status "NA_FILA"
    E o saldo da peca 55 passa a ser 6
    Quando o mecanico "Carlos" inicia a execucao da OS 1042
    Entao a execucao da OS 1042 fica com status "EM_EXECUCAO"
    E o evento "ExecucaoIniciada" e publicado
    Quando o mecanico finaliza o servico 7 da OS 1042
    E o mecanico finaliza o servico 3 da OS 1042
    E a execucao da OS 1042 e finalizada
    Entao a execucao da OS 1042 fica com status "FINALIZADA"
    E o evento "ExecucaoFinalizada" e publicado
    E o saldo da peca 55 passa a ser 6

  Cenario: Falta de peca deixa a OS aguardando, e ela segue quando a peca chega
    Dado que existem 1 unidades da peca 55 em estoque
    Quando chega o comando para enfileirar a OS 2000 com os servicos 7 e 3 e 4 unidades da peca 55
    Entao a execucao da OS 2000 fica com status "AGUARDANDO_ITENS"
    E o evento "ExecucaoAguardandoItens" e publicado
    E o mecanico "Carlos" nao consegue iniciar a execucao da OS 2000
    Quando chegam mais 10 unidades da peca 55 pela nota "NF-901"
    E o mecanico "Carlos" inicia a execucao da OS 2000
    Entao a execucao da OS 2000 fica com status "EM_EXECUCAO"
    E o saldo da peca 55 passa a ser 7

  Cenario: Falha na execucao devolve as pecas e dispara a compensacao
    Dado que existem 10 unidades da peca 55 em estoque
    Quando chega o comando para enfileirar a OS 3000 com os servicos 7 e 3 e 4 unidades da peca 55
    E o mecanico "Carlos" inicia a execucao da OS 3000
    E a execucao da OS 3000 falha por "suspensao comprometida"
    Entao a execucao da OS 3000 fica com status "FALHOU"
    E o saldo da peca 55 passa a ser 10
    E o evento "ExecucaoFalhou" e publicado

  Cenario: A mesma mensagem entregue duas vezes nao duplica nada
    Dado que existem 10 unidades da peca 55 em estoque
    Quando chega o comando para enfileirar a OS 4000 com os servicos 7 e 3 e 4 unidades da peca 55
    E chega o comando para enfileirar a OS 4000 com os servicos 7 e 3 e 4 unidades da peca 55
    Entao a fila da oficina tem 1 execucao
    E o saldo da peca 55 passa a ser 6
