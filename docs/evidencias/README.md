# Evidencias

Suba aqui o print do relatorio de cobertura, exigido pelo enunciado da Fase 4.

Como gerar:

```bash
mvn verify
# abra target/site/jacoco/index.html no navegador
# tire o print da tabela de cobertura e salve como cobertura.png
```

Depois, troque a linha correspondente na secao "Evidencia de cobertura" do
README da raiz por:

```markdown
![Cobertura de testes](docs/evidencias/cobertura.png)
```
