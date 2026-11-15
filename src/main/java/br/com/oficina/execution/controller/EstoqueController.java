package br.com.oficina.execution.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.execution.dto.EntradaEstoqueRequest;
import br.com.oficina.execution.dto.SaldoEstoqueResponse;
import br.com.oficina.execution.service.EstoqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Estoque do almoxarifado: quanto ha de cada peca, e a entrada de pecas novas.
 *
 * Aqui so existe quantidade. Nome e preco da peca sao do catalogo, no Billing;
 * os dois se ligam pelo itemId.
 */
@RestController
@RequestMapping("/execution/v1/estoque")
@RequiredArgsConstructor
public class EstoqueController {

	private final EstoqueService estoqueService;

	@GetMapping("/{itemId}")
	public SaldoEstoqueResponse saldo(@PathVariable Long itemId) {
		return new SaldoEstoqueResponse(itemId, estoqueService.saldoDe(itemId));
	}

	@PostMapping("/{itemId}/entradas")
	public SaldoEstoqueResponse registrarEntrada(
			@PathVariable Long itemId,
			@Valid @RequestBody EntradaEstoqueRequest request
	) {
		int saldo = estoqueService.registrarEntrada(request.referencia(), itemId, request.quantidade());
		return new SaldoEstoqueResponse(itemId, saldo);
	}
}
