package br.com.oficina.execution.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.execution.dto.ExecucaoResponse;
import br.com.oficina.execution.dto.FalharExecucaoRequest;
import br.com.oficina.execution.dto.FinalizarServicoRequest;
import br.com.oficina.execution.dto.IniciarExecucaoRequest;
import br.com.oficina.execution.service.ExecucaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * O que a tela da oficina chama: ver a fila e acompanhar o trabalho do mecanico.
 *
 * Nao ha endpoint para COLOCAR uma OS na fila. Isso chega por mensagem, vinda
 * do OS Service (veja ComandoHandler), porque a integracao entre os servicos e
 * so por mensageria.
 */
@RestController
@RequestMapping("/execution/v1/execucoes")
@RequiredArgsConstructor
public class ExecucaoController {

	private final ExecucaoService execucaoService;

	/** A fila da oficina, do carro que espera ha mais tempo para o mais recente. */
	@GetMapping("/fila")
	public List<ExecucaoResponse> fila() {
		return execucaoService.listarFila().stream().map(ExecucaoResponse::de).toList();
	}

	@GetMapping("/{ordemServicoId}")
	public ExecucaoResponse buscar(@PathVariable Long ordemServicoId) {
		return ExecucaoResponse.de(execucaoService.buscarPorOrdemServico(ordemServicoId));
	}

	@PostMapping("/{ordemServicoId}/iniciar")
	public ExecucaoResponse iniciar(
			@PathVariable Long ordemServicoId,
			@Valid @RequestBody IniciarExecucaoRequest request
	) {
		return ExecucaoResponse.de(execucaoService.iniciar(ordemServicoId, request.mecanico()));
	}

	@PostMapping("/{ordemServicoId}/servicos/{servicoId}/finalizar")
	public ExecucaoResponse finalizarServico(
			@PathVariable Long ordemServicoId,
			@PathVariable Long servicoId,
			@Valid @RequestBody FinalizarServicoRequest request
	) {
		return ExecucaoResponse.de(
				execucaoService.finalizarServico(ordemServicoId, servicoId, request.observacao()));
	}

	@PostMapping("/{ordemServicoId}/finalizar")
	public ExecucaoResponse finalizar(@PathVariable Long ordemServicoId) {
		return ExecucaoResponse.de(execucaoService.finalizar(ordemServicoId));
	}

	/** Algo quebrou e o servico nao sera entregue. Dispara o estorno do pagamento. */
	@PostMapping("/{ordemServicoId}/falhar")
	public ExecucaoResponse falhar(
			@PathVariable Long ordemServicoId,
			@Valid @RequestBody FalharExecucaoRequest request
	) {
		return ExecucaoResponse.de(
				execucaoService.falhar(ordemServicoId, request.motivo(), request.etapa()));
	}
}
