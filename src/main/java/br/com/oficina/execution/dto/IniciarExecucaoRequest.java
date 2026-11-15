package br.com.oficina.execution.dto;

import jakarta.validation.constraints.NotBlank;

public record IniciarExecucaoRequest(@NotBlank String mecanico) {
}
