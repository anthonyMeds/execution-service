package br.com.oficina.execution.adapter.in.web.dto;

import java.time.Instant;

public record ErroResponse(int status, String erro, String mensagem, Instant em) {

	public static ErroResponse de(int status, String erro, String mensagem) {
		return new ErroResponse(status, erro, mensagem, Instant.now());
	}
}
