package br.com.oficina.execution.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.oficina.execution.model.MovimentacaoEstoque;
import br.com.oficina.execution.model.PecaRequisitada;
import br.com.oficina.execution.model.TipoMovimentacao;
import br.com.oficina.execution.repository.MovimentacaoEstoqueRepository;
import lombok.RequiredArgsConstructor;

/**
 * Estoque do almoxarifado.
 *
 * O saldo nunca e gravado: ele e a soma das movimentacoes de cada peca. Para
 * mudar o saldo, grava-se uma movimentacao nova.
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

	private final MovimentacaoEstoqueRepository movimentacaoRepository;

	public int saldoDe(Long itemId) {
		return movimentacaoRepository.findByItemId(itemId).stream()
				.mapToInt(MovimentacaoEstoque::getEfeitoNoSaldo)
				.sum();
	}

	/**
	 * Peca chegou do fornecedor. A referencia e a nota fiscal: lancar a mesma
	 * nota duas vezes nao dobra o estoque.
	 */
	public int registrarEntrada(String referencia, Long itemId, int quantidade) {
		if (referencia == null || referencia.isBlank()) {
			throw new IllegalArgumentException("a referencia da entrada e obrigatoria");
		}
		if (quantidade <= 0) {
			throw new IllegalArgumentException("a quantidade de entrada deve ser maior que zero");
		}
		registrarUmaVez(referencia, itemId, quantidade, TipoMovimentacao.ENTRADA);
		return saldoDe(itemId);
	}

	/**
	 * Separa as pecas de uma OS. Devolve os ids das pecas que NAO tinham saldo;
	 * lista vazia quer dizer que tudo foi reservado.
	 *
	 * Pode ser chamado de novo para a mesma saga: o que ja estava reservado e
	 * pulado, e so se tenta o que faltava.
	 */
	public List<Long> reservar(String sagaId, List<PecaRequisitada> pecas) {
		List<Long> emFalta = new ArrayList<>();
		for (PecaRequisitada peca : pecas) {
			if (jaRegistrada(sagaId, peca.itemId(), TipoMovimentacao.RESERVA)) {
				continue;
			}
			if (saldoDe(peca.itemId()) < peca.quantidade()) {
				emFalta.add(peca.itemId());
				continue;
			}
			registrar(sagaId, peca.itemId(), peca.quantidade(), TipoMovimentacao.RESERVA);
		}
		return emFalta;
	}

	/** O servico foi feito: a reserva vira consumo definitivo. */
	public void darBaixa(String sagaId, List<PecaRequisitada> pecas) {
		for (PecaRequisitada peca : pecas) {
			registrarUmaVez(sagaId, peca.itemId(), peca.quantidade(), TipoMovimentacao.BAIXA);
		}
	}

	/**
	 * A execucao nao vai acontecer: as pecas voltam para a prateleira.
	 * So devolve o que de fato tinha sido reservado.
	 */
	public void devolver(String sagaId, List<PecaRequisitada> pecas) {
		for (PecaRequisitada peca : pecas) {
			if (jaRegistrada(sagaId, peca.itemId(), TipoMovimentacao.RESERVA)) {
				registrarUmaVez(sagaId, peca.itemId(), peca.quantidade(), TipoMovimentacao.ESTORNO);
			}
		}
	}

	private void registrarUmaVez(String referencia, Long itemId, int quantidade, TipoMovimentacao tipo) {
		if (!jaRegistrada(referencia, itemId, tipo)) {
			registrar(referencia, itemId, quantidade, tipo);
		}
	}

	private boolean jaRegistrada(String referencia, Long itemId, TipoMovimentacao tipo) {
		return movimentacaoRepository.existsByReferenciaAndItemIdAndTipo(referencia, itemId, tipo);
	}

	private void registrar(String referencia, Long itemId, int quantidade, TipoMovimentacao tipo) {
		movimentacaoRepository.save(new MovimentacaoEstoque(referencia, itemId, quantidade, tipo));
	}
}
