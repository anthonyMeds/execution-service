package br.com.oficina.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** A referencia e a nota fiscal ou ordem de compra que trouxe a peca. */
public record EntradaEstoqueRequest(@NotBlank String referencia, @Positive int quantidade) {
}
