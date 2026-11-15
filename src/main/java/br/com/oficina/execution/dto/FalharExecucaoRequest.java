package br.com.oficina.execution.dto;

import jakarta.validation.constraints.NotBlank;

/** A etapa e livre: em que ponto do trabalho a falha aconteceu. */
public record FalharExecucaoRequest(@NotBlank String motivo, String etapa) {
}
