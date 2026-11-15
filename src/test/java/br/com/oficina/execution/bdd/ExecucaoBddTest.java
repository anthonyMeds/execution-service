package br.com.oficina.execution.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.cucumber.core.cli.Main;

/**
 * Roda todos os cenarios de src/test/resources/features dentro do mvn verify.
 *
 * O Cucumber le os arquivos .feature, casa cada frase com um metodo de
 * ExecucaoSteps e devolve 0 se todos os cenarios passaram. O relatorio em HTML
 * fica em target/cucumber/relatorio-bdd.html.
 */
class ExecucaoBddTest {

	@Test
	@DisplayName("todos os cenarios BDD passam")
	void todosOsCenariosPassam() {
		byte resultado = Main.run(
				new String[] {
						"--glue", "br.com.oficina.execution.bdd",
						"--plugin", "pretty",
						"--plugin", "html:target/cucumber/relatorio-bdd.html",
						"--monochrome",
						"classpath:features"
				},
				Thread.currentThread().getContextClassLoader());

		assertEquals(0, resultado, "ha cenario BDD falhando; veja a saida acima");
	}
}
