package br.com.oficina.execution.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.oficina.execution.dto.ErroResponse;
import br.com.oficina.execution.exception.ExecucaoNaoEncontradaException;
import br.com.oficina.execution.exception.TransicaoInvalidaException;

/** Transforma as excecoes de negocio na resposta HTTP certa, para todos os controllers. */
@RestControllerAdvice
public class TratadorDeErros {

	@ExceptionHandler(ExecucaoNaoEncontradaException.class)
	public ResponseEntity<ErroResponse> naoEncontrada(ExecucaoNaoEncontradaException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErroResponse.de(404, "EXECUCAO_NAO_ENCONTRADA", e.getMessage()));
	}

	/** 409: o pedido e valido, mas o estado atual da execucao nao permite. */
	@ExceptionHandler(TransicaoInvalidaException.class)
	public ResponseEntity<ErroResponse> transicaoInvalida(TransicaoInvalidaException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErroResponse.de(409, "TRANSICAO_INVALIDA", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErroResponse> argumentoInvalido(IllegalArgumentException e) {
		return ResponseEntity.badRequest()
				.body(ErroResponse.de(400, "REQUISICAO_INVALIDA", e.getMessage()));
	}
}
