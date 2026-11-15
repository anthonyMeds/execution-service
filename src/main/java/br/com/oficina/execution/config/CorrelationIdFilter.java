package br.com.oficina.execution.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Da um identificador a cada requisicao e o coloca em todas as linhas de log.
 *
 * Para que: com tres servicos escrevendo log ao mesmo tempo, e por este id que
 * se filtra "so as linhas desta requisicao". O API Gateway ja manda o id no
 * cabecalho x-correlation-id; se nao vier, criamos um.
 *
 * MDC e uma "etiqueta" que o logger carrega durante a requisicao; o padrao de
 * log no application.yml imprime essa etiqueta em toda linha.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "x-correlation-id";
	public static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String correlationId = request.getHeader(HEADER);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = UUID.randomUUID().toString();
		}

		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER, correlationId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
