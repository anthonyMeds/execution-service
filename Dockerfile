# Build em duas etapas: a imagem final nao carrega Maven nem codigo-fonte.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# O pom entra sozinho primeiro para a camada de dependencias ser reaproveitada
# enquanto so o codigo muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Usuario sem privilegio: o container nao roda como root.
RUN addgroup -S oficina && adduser -S oficina -G oficina
COPY --from=build /app/target/execution-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R oficina:oficina /app
USER oficina
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
