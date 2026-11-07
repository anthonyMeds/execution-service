package br.com.oficina.execution.adapter.in.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mesmo padrao do CorrelationIdFilter da Fase 3: pega o x-correlation-id que o
 * API Gateway injeta, poe no MDC para sair em todo log, e devolve no response.
 *
 * Aqui ele importa ainda mais do que no monolito: o mesmo id vai no envelope das
 * mensagens, entao da para seguir uma OS do Gateway ate a compensacao no Billing
 * atravessando os tres servicos.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "x-correlation-id";
	public static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain
	) throws ServletException, IOException {
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
