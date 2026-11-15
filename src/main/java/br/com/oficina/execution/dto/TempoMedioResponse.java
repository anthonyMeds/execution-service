package br.com.oficina.execution.dto;

import java.util.List;

/**
 * Uma linha do relatorio de tempo medio.
 *
 * @param amostras quantas execucoes (ou servicos) entraram na media
 */
public record TempoMedioResponse(String chave, String descricao, double mediaMinutos, int amostras) {

	public static TempoMedioResponse de(String chave, String descricao, List<Long> duracoesEmSegundos) {
		if (duracoesEmSegundos.isEmpty()) {
			return new TempoMedioResponse(chave, descricao, 0.0, 0);
		}
		double mediaSegundos = duracoesEmSegundos.stream().mapToLong(Long::longValue).average().orElse(0.0);
		double mediaMinutos = Math.round(mediaSegundos / 60.0 * 100.0) / 100.0;
		return new TempoMedioResponse(chave, descricao, mediaMinutos, duracoesEmSegundos.size());
	}
}
