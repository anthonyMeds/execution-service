package br.com.oficina.execution.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.execution.dto.TempoMedioResponse;
import br.com.oficina.execution.service.RelatorioService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/execution/v1/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

	private final RelatorioService relatorioService;

	@GetMapping("/tempo-medio-os")
	public TempoMedioResponse tempoMedioDeOrdemServico() {
		return relatorioService.tempoMedioDeOrdemServico();
	}

	@GetMapping("/tempo-medio-servicos")
	public List<TempoMedioResponse> tempoMedioPorServico() {
		return relatorioService.tempoMedioPorServico();
	}
}
