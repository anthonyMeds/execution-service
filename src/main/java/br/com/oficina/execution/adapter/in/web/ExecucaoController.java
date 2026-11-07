package br.com.oficina.execution.adapter.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.execution.adapter.in.web.dto.EncerrarExecucaoRequest;
import br.com.oficina.execution.adapter.in.web.dto.ExecucaoResponse;
import br.com.oficina.execution.adapter.in.web.dto.FinalizarServicoRequest;
import br.com.oficina.execution.adapter.in.web.dto.IniciarExecucaoRequest;
import br.com.oficina.execution.core.usecase.ConsultarExecucaoUseCase;
import br.com.oficina.execution.core.usecase.EncerrarExecucaoUseCase;
import br.com.oficina.execution.core.usecase.FinalizarExecucaoUseCase;
import br.com.oficina.execution.core.usecase.FinalizarServicoUseCase;
import br.com.oficina.execution.core.usecase.IniciarExecucaoUseCase;
import jakarta.validation.Valid;

/**
 * As transicoes que o mecanico aciona na oficina, mais as consultas.
 *
 * A entrada da saga (EnfileirarExecucao) nao esta aqui de proposito: ela chega
 * por mensagem, nao por HTTP. Expor isso como endpoint abriria uma porta para
 * outro servico chamar este diretamente, que e exatamente o que o contrato do
 * time proibe.
 */
@RestController
@RequestMapping("/execution/v1/execucoes")
public class ExecucaoController {

	private final ConsultarExecucaoUseCase consultar;
	private final IniciarExecucaoUseCase iniciar;
	private final FinalizarServicoUseCase finalizarServico;
	private final FinalizarExecucaoUseCase finalizar;
	private final EncerrarExecucaoUseCase encerrar;

	public ExecucaoController(
			ConsultarExecucaoUseCase consultar,
			IniciarExecucaoUseCase iniciar,
			FinalizarServicoUseCase finalizarServico,
			FinalizarExecucaoUseCase finalizar,
			EncerrarExecucaoUseCase encerrar
	) {
		this.consultar = consultar;
		this.iniciar = iniciar;
		this.finalizarServico = finalizarServico;
		this.finalizar = finalizar;
		this.encerrar = encerrar;
	}

	@GetMapping("/fila")
	public List<ExecucaoResponse> fila() {
		return consultar.fila().stream().map(ExecucaoResponse::de).toList();
	}

	@GetMapping("/{ordemServicoId}")
	public ExecucaoResponse porOrdemServico(@PathVariable Long ordemServicoId) {
		return ExecucaoResponse.de(consultar.porOrdemServico(ordemServicoId));
	}

	@PostMapping("/{ordemServicoId}/iniciar")
	public ResponseEntity<ExecucaoResponse> iniciar(
			@PathVariable Long ordemServicoId,
			@Valid @RequestBody IniciarExecucaoRequest request
	) {
		return ResponseEntity.ok(ExecucaoResponse.de(iniciar.executar(ordemServicoId, request.mecanico())));
	}

	@PostMapping("/{ordemServicoId}/servicos/{servicoId}/finalizar")
	public ResponseEntity<ExecucaoResponse> finalizarServico(
			@PathVariable Long ordemServicoId,
			@PathVariable Long servicoId,
			@Valid @RequestBody FinalizarServicoRequest request
	) {
		return ResponseEntity.ok(ExecucaoResponse.de(
				finalizarServico.executar(ordemServicoId, servicoId, request.observacao())));
	}

	@PostMapping("/{ordemServicoId}/finalizar")
	public ResponseEntity<ExecucaoResponse> finalizar(@PathVariable Long ordemServicoId) {
		return ResponseEntity.ok(ExecucaoResponse.de(finalizar.executar(ordemServicoId)));
	}

	@PostMapping("/{ordemServicoId}/falhar")
	public ResponseEntity<ExecucaoResponse> falhar(
			@PathVariable Long ordemServicoId,
			@Valid @RequestBody EncerrarExecucaoRequest request
	) {
		return ResponseEntity.ok(ExecucaoResponse.de(
				encerrar.falhar(ordemServicoId, request.motivo(), request.etapa())));
	}
}
