package br.com.oficina.execution.dto;

import jakarta.validation.constraints.Size;

public record FinalizarServicoRequest(@Size(max = 500) String observacao) {
}
