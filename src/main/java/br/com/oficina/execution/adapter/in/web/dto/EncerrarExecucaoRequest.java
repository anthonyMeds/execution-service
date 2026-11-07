package br.com.oficina.execution.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record EncerrarExecucaoRequest(@NotBlank String motivo, String etapa) {
}
