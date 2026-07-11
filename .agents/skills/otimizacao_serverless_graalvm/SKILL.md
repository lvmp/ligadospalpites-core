---
name: otimizacao_serverless_graalvm
description: Diretrizes e configurações para compilação nativa com GraalVM e otimização de cold starts em Spring Boot 4.1.0 rodando em contêineres serverless.
---

# Otimização Serverless & GraalVM Native Image

Esta skill define as boas práticas de otimização de desempenho e tempo de inicialização (*cold start*) para contêineres **Google Cloud Run** rodando **Spring Boot 4.1.0** e **Kotlin**.

---

## 1. Diretrizes para Compilação Nativa (GraalVM AOT)

Ao utilizar imagens nativas compila-se o bytecode Java em código de máquina específico do sistema operacional. Isso elimina a necessidade de inicialização do JIT compiler, derrubando o tempo de boot de segundos para dezenas de milissegundos.

### A. Registro de Dynamic Reflection Hints
Imagens nativas exigem que toda reflexão dinámica, proxy JDK ou carregamento dinâmico de recursos em tempo de execução seja declarado previamente. Ao escrever lógica que serializa ou desserializa JSON dinâmico (por exemplo, payloads JSON da API de Esportes), registre as classes explicitamente utilizando `@RegisterReflectionForBinding` ou declare um `RuntimeHintsRegistrar`.

```kotlin
@Configuration
@ImportRuntimeHints(SportsFeedHints::class)
class SportsFeedConfig

class SportsFeedHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Registrar reflexão para DTOs externos que não usam Jackson padrão
        hints.reflection().registerType(ExternalFixtureDto::class.java, MemberCategory.values())
    }
}
```

### B. Evitando Proxies Dinâmicos do Spring
Tente utilizar injeção direta de construtores ao invés de anotações complexas que necessitem de criação de proxies reflexivos em runtime. Prefira injeção estática e composição simples.

---

## 2. Configurações de Inicialização Rápida no application.yml

Para instâncias destinadas a ambientes de desenvolvimento local ou deploys de teste onde o tempo de boot é prioridade, utilize a inicialização preguiçosa (*Lazy Initialization*):

```yaml
spring:
  main:
    lazy-initialization: true # Inicializa os Beans apenas quando forem requisitados
```
*Atenção*: Em produção (`application-prod.yml`), a inicialização preguiçosa deve ser avaliada com cuidado para evitar latências extras na primeira requisição HTTP do usuário.

---

## 3. Parametrização da JVM no Dockerfile (Não-Nativo)

Se a imagem nativa não for viável no pipeline imediato de CI/CD e a execução utilizar a JVM padrão (JBR ou OpenJDK), aplique os seguintes parâmetros na inicialização do contêiner no `Dockerfile` para otimizar memória e tempo de boot em recursos limitados (ex: 512MB RAM):

```dockerfile
# Comando de inicialização otimizado para containers serverless
ENTRYPOINT ["java", \
            "-XX:+UseSerialGC", \
            "-Xss256k", \
            "-XX:TieredStopAtLevel=1", \
            "-Dspring.backgroundpreinitializer=false", \
            "-jar", \
            "app.jar"]
```

### Racional das Opções JVM:
* `-XX:+UseSerialGC`: O Garbage Collector Serial consome significativamente menos memória RAM do que o G1GC, ideal para ambientes de microsserviços pequenos (menores de 1GB).
* `-XX:TieredStopAtLevel=1`: Para a compilação JIT no nível 1 (apenas compilação C1 sem profiling). Reduz drasticamente o tempo de boot inicial do framework Spring, aceitando uma pequena perda de throughput em execuções de longo prazo (perfeito para funções serverless que morrem após minutos).
* `-Dspring.backgroundpreinitializer=false`: Desabilita a inicialização paralela de formatadores e validadores em segundo plano que causam concorrência de CPU em contêineres com limite de 1 vCPU.
