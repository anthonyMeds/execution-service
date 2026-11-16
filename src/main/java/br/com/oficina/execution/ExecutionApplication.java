package br.com.oficina.execution;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(
		title = "Execution Service",
		version = "v1",
		description = "Fila da oficina, execucao dos servicos, estoque de pecas e relatorios. "
				+ "As execucoes chegam pelo RabbitMQ (EnfileirarExecucao, vindo do OS Service); "
				+ "esta API e a do mecanico e do estoque."))
@SpringBootApplication
public class ExecutionApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExecutionApplication.class, args);
	}
}
