package br.com.oficina.execution.model;

/**
 * Uma peca que nao tinha saldo suficiente para a OS.
 *
 * Vai dentro do evento ExecucaoAguardandoItens, no formato combinado com o OS
 * Service: quanto a OS precisa e quanto havia em estoque naquele momento.
 */
public record ItemEmFalta(Long itemId, int quantidadeNecessaria, int quantidadeDisponivel) {
}
