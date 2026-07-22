# Stage 1: Build da aplicação
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
# Garante permissões de execução do gradle wrapper
RUN chmod +x ./gradlew
# Compila o JAR do Spring Boot ignorando os testes de integração
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Ambiente de execução otimizado e leve (JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Configuração de segurança: Usuário não-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia o artefato compilado
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# Inicialização altamente otimizada para recursos severamente limitados (GCP Free-Tier 512MB)
ENTRYPOINT ["java", \
            "-XX:+UseSerialGC", \
            "-Xss256k", \
            "-XX:TieredStopAtLevel=1", \
            "-Dspring.backgroundpreinitializer=false", \
            "-jar", \
            "app.jar"]
