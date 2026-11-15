package br.com.oficina.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.oficina.execution.dto.TempoMedioResponse;
import br.com.oficina.execution.model.Execucao;
import br.com.oficina.execution.model.ServicoExecucao;
import br.com.oficina.execution.model.StatusExecucao;
import br.com.oficina.execution.repository.ExecucaoRepository;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

	@Mock
	private ExecucaoRepository execucaoRepository;

	@InjectMocks
	private RelatorioService relatorioService;

	/** Execucao de mentira com duracao escolhida, para a conta nao depender do relogio. */
	private Execucao finalizada(long duracaoSegundos, ServicoExecucao... servicos) {
		Execucao execucao = mock(Execucao.class);
		lenient().when(execucao.duracaoSegundos()).thenReturn(duracaoSegundos);
		lenient().when(execucao.getServicos()).thenReturn(List.of(servicos));
		return execucao;
	}

	private ServicoExecucao servico(Long id, String nome, long duracaoSegundos, boolean concluido) {
		ServicoExecucao servico = mock(ServicoExecucao.class);
		lenient().when(servico.getServicoId()).thenReturn(id);
		lenient().when(servico.getNome()).thenReturn(nome);
		lenient().when(servico.duracaoSegundos()).thenReturn(duracaoSegundos);
		lenient().when(servico.concluido()).thenReturn(concluido);
		return servico;
	}

	@Test
	@DisplayName("tempo medio de OS: media das execucoes finalizadas, em minutos")
	void tempoMedioDeOrdemServico() {
		List<Execucao> finalizadas = List.of(finalizada(3600), finalizada(7200));
		when(execucaoRepository.findByStatus(StatusExecucao.FINALIZADA)).thenReturn(finalizadas);

		TempoMedioResponse media = relatorioService.tempoMedioDeOrdemServico();

		assertEquals(90.0, media.mediaMinutos());
		assertEquals(2, media.amostras());
	}

	@Test
	@DisplayName("sem nenhuma execucao finalizada: zero, sem erro")
	void semDados() {
		when(execucaoRepository.findByStatus(StatusExecucao.FINALIZADA)).thenReturn(List.of());

		assertEquals(0, relatorioService.tempoMedioDeOrdemServico().amostras());
		assertEquals(0.0, relatorioService.tempoMedioDeOrdemServico().mediaMinutos());
		assertEquals(0, relatorioService.tempoMedioPorServico().size());
	}

	@Test
	@DisplayName("tempo medio por servico: agrupa pelo id e ignora servico nao concluido")
	void tempoMedioPorServico() {
		List<Execucao> finalizadas = List.of(
				finalizada(0, servico(7L, "Pastilhas", 1800, true), servico(3L, "Alinhamento", 1200, true)),
				finalizada(0, servico(7L, "Pastilhas", 3000, true), servico(9L, "Cancelado", 500, false)));
		when(execucaoRepository.findByStatus(StatusExecucao.FINALIZADA)).thenReturn(finalizadas);

		List<TempoMedioResponse> relatorio = relatorioService.tempoMedioPorServico();

		assertEquals(2, relatorio.size());
		TempoMedioResponse pastilhas = relatorio.get(0);
		assertEquals("7", pastilhas.chave());
		assertEquals("Pastilhas", pastilhas.descricao());
		assertEquals(40.0, pastilhas.mediaMinutos());
		assertEquals(2, pastilhas.amostras());
		assertEquals(20.0, relatorio.get(1).mediaMinutos());
	}
}
