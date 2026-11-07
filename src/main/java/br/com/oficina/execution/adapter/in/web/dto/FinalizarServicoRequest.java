package br.com.oficina.execution.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record FinalizarServicoRequest(@Size(max = 500) String observacao) {
}
