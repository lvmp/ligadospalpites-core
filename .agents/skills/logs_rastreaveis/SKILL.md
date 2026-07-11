---
name: logs_rastreaveis
description: Diretrizes e boas práticas para estruturar logs padronizados, estruturados (JSON) e rastreáveis usando traceId/correlationId no Spring Boot com Kotlin.
---

# Logs Padronizados e Rastreáveis via TraceId

Esta skill orienta o agente na configuração e na escrita de logs de alta qualidade em projetos **Kotlin** e **Spring Boot**. Foca em rastreabilidade de requisições fim-a-fim através de um identificador único de rastreamento (**TraceId** ou *CorrelationId*), formatação estruturada para produção (JSON) e propagação correta em fluxos assíncronos.

---

## 💡 Princípios de Rastreabilidade e MDC

Em sistemas distribuídos ou mesmo monólitos modulares, é crucial correlacionar todos os logs de uma mesma requisição. Usamos o **MDC (Mapped Diagnostic Context)** do SLF4J para armazenar variáveis contextuais (como `traceId`, `userId`) associadas à thread atual. O framework de log (como Logback) lê o MDC automaticamente e injeta esses dados em cada linha de log.

### Regra de Ouro:
Toda requisição que entra no sistema (REST, Fila, Consumidor, Cron) deve:
1. Extrair o `traceId` do cabeçalho de entrada (ex: `X-Trace-Id` ou `X-Correlation-Id`), ou gerar um novo UUID se não existir.
2. Injetar o `traceId` no MDC.
3. Garantir a limpeza do MDC ao final da requisição (bloco `finally`) para evitar vazamento de contexto para outras requisições que reutilizem a thread.

---

## 🛠️ Abordagens de Implementação no Spring Boot

### Opção A: Usando Micrometer Tracing (Recomendado para Spring Boot 4.x)

O **Micrometer Tracing** (sucessor do Spring Cloud Sleuth) automatiza todo o ciclo de vida do traceId, gerando e propagando-o por chamadas HTTP externas e injetando-o no MDC automaticamente.

#### 1. Dependências (no `build.gradle.kts`):
```kotlin
implementation("io.micrometer:micrometer-tracing-bridge-otel") // Ou 'micrometer-tracing-bridge-brave'
```

O Micrometer Tracing injetará automaticamente as chaves `traceId` e `spanId` no MDC.

---

### Opção B: Filtro Personalizado (Abordagem Leve / Customizada)

Se preferir uma solução sem dependências pesadas de tracing distribuído, implemente um `OncePerRequestFilter` personalizado:

#### 1. Implementação do Filtro:
```kotlin
package com.ligadospalpites.infrastructure.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        private const val TRACE_ID_KEY = "traceId"
        private const val TRACE_HEADER = "X-Trace-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Recupera o traceId do cabeçalho ou gera um novo
        val traceId = request.getHeader(TRACE_HEADER) ?: UUID.randomUUID().toString()
        
        MDC.put(TRACE_ID_KEY, traceId)
        
        // Inclui o traceId no cabeçalho de resposta para facilitar o debug pelo cliente
        response.addHeader(TRACE_HEADER, traceId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            // CRÍTICO: Limpa o MDC para liberar a thread
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
```


---

## 📋 Padronização de Logs por Camada (HTTP & UseCases)

Para garantir que os logs sejam úteis e uniformes, definimos padrões de log específicos para as principais fronteiras de entrada e processamento do sistema.

### 1. Chamadas HTTP (REST Controllers e Spring Security)
Todos os requests e responses HTTP devem ser logados. No fluxo HTTP, é crucial garantir que as respostas geradas automaticamente pelo framework (como bloqueios de autenticação/autorização do Spring Security) **nunca respondam silenciosamente**.

#### Capturando Respostas de Segurança (Ex: 403 Forbidden e 401 Unauthorized)
Por padrão, filtros do Spring Security interceptam e bloqueiam chamadas inválidas no início da cadeia de filtros, respondendo ao cliente sem passar pela nossa lógica ou filtros comuns. Para evitar respostas silenciosas e garantir que esses erros apareçam nos logs com o `traceId` correspondente:

1. **Posicionamento do Filtro de Log:** Garanta que o filtro de log (seja o `TraceIdFilter` ou o filtro do `Logbook`) esteja posicionado **antes** da cadeia de filtros do Spring Security para que o `traceId` seja atribuído antes de qualquer bloqueio.
   ```kotlin
   // Na configuração de segurança do Spring Security (ex: SecurityFilterChain)
   http.addFilterBefore(traceIdFilter, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter::class.java)
   ```

2. **AccessDeniedHandler e AuthenticationEntryPoint customizados:** Configure comportamentos explícitos na segurança do Spring para capturar e registrar os logs de acessos não autorizados.
   ```kotlin
   @Component
   class CustomAccessDeniedHandler : AccessDeniedHandler {
       private val log = KotlinLogging.logger {}

       override fun handle(
           request: HttpServletRequest,
           response: HttpServletResponse,
           accessDeniedException: AccessDeniedException
       ) {
           // O log exibe explicitamente qual rota protegida foi bloqueada e o motivo
           log.warn { "Acesso negado para a rota [${request.method}] ${request.requestURI} - Motivo: ${accessDeniedException.message}" }
           response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
       }
   }
   ```
   *Nota: Registre o `CustomAccessDeniedHandler` e um `AuthenticationEntryPoint` equivalente nas configurações do `HttpSecurity` (`exceptionHandling { ... }`).*

---

### 2. Casos de Uso (Use Cases)
A camada de aplicação (Casos de Uso) deve registrar o início e o término das operações de negócio importantes, o que serve como auditoria de execução.

*   **Início:** Deve informar o nome do caso de uso e parâmetros de identificação essenciais (como IDs de entidades ou usuários), sempre omitindo senhas ou tokens.
*   **Fim:** Deve registrar o sucesso e informações de resultado parciais úteis.
*   **Exceções:**
    *   **Lógicas de negócio esperadas (ex: saldo insuficiente, palpite duplicado):** Logar no nível `WARN`, contendo mensagens que expliquem a falha lógica.
    *   **Falhas de infraestrutura inesperadas (ex: banco offline, timeout):** Logar no nível `ERROR` acompanhado de toda a stack trace do erro.

```kotlin
@Service
class RegistrarPalpiteUseCase(
    private val repository: PalpiteRepository
) {
    private val log = KotlinLogging.logger {}

    operator fun invoke(command: Command): Result {
        // Log de Início
        log.info { "Iniciando caso de uso 'RegistrarPalpite' para o usuarioId: ${command.usuarioId} no jogoId: ${command.jogoId}" }

        try {
            val palpite = ...
            val salvo = repository.save(palpite)
            
            // Log de Sucesso
            log.info { "Caso de uso 'RegistrarPalpite' concluído com sucesso para o palpiteId: ${salvo.id}" }
            return Result(salvo.id)
        } catch (ex: PalpiteDuplicadoException) {
            // Fluxo alternativo esperado de negócio
            log.warn { "Palpite não registrado: Usuário ${command.usuarioId} já possui palpite para o jogo ${command.jogoId}" }
            throw ex
        } catch (ex: Exception) {
            // Falha inesperada de sistema/infraestrutura
            log.error(ex) { "Erro crítico ao registrar palpite para o usuarioId: ${command.usuarioId}" }
            throw ex
        }
    }
}
```

---

### 3. Consumidores de Fila (RabbitMQ / Kafka)
Eventos consumidos de filas são portas de entrada assíncronas. O consumidor deve verificar se a mensagem contém um `traceId` em seus cabeçalhos/metadados e inseri-lo no MDC. Caso a mensagem não possua (ex: gerada por sistemas legados), gere um novo UUID para garantir o rastreamento do processamento local.

```kotlin
@Component
class PalpiteEventConsumer {
    private val log = KotlinLogging.logger {}

    @RabbitListener(queues = ["fila.palpites"])
    fun processarMensagem(mensagem: PalpiteMensagem, @Header("X-Trace-Id") traceIdHeader: String?) {
        val traceId = traceIdHeader ?: UUID.randomUUID().toString()
        MDC.put("traceId", traceId)

        try {
            log.info { "Iniciando consumo de mensagem de palpite - Mensagem ID: ${mensagem.id}" }
            // Executa a lógica de negócio ou chama UseCase...
            log.info { "Consumo de mensagem de palpite concluído com sucesso" }
        } catch (ex: Exception) {
            log.error(ex) { "Erro crítico no processamento da mensagem de palpite" }
        } finally {
            MDC.remove("traceId") // Garante a liberação da thread do pool do listener
        }
    }
}
```

---

### 4. Tarefas Agendadas (Cron Jobs / @Scheduled)
Processos iniciados por agendadores automáticos rodam em threads específicas gerenciadas pelo Spring TaskScheduler. Como não há gatilho externo (HTTP/Fila), a própria função do agendador deve criar e registrar um novo `traceId` imediatamente ao iniciar. 
Recomenda-se prefixar o `traceId` com o nome ou tipo do job (ex: `cron-[nome-do-job]-[UUID-parcial]`) para rápida identificação nos logs de produção.

```kotlin
@Component
class FechamentoRodadaScheduler {
    private val log = KotlinLogging.logger {}

    @Scheduled(cron = "0 0 3 * * *") // Executa diariamente às 3h
    fun fecharRodadas() {
        val uniqueId = UUID.randomUUID().toString().take(8)
        val traceId = "cron-fechamento-rodada-$uniqueId"
        MDC.put("traceId", traceId)

        try {
            log.info { "Iniciando processamento agendado para fechamento de rodadas" }
            // Executa a lógica ou chama UseCase...
            log.info { "Processamento agendado concluído com sucesso" }
        } catch (ex: Exception) {
            log.error(ex) { "Erro crítico na execução do scheduler de fechamento de rodadas" }
        } finally {
            MDC.remove("traceId") // Garante que a thread de agendamento fique limpa
        }
    }
}
```

---


## 📄 Configuração do Logback (`logback-spring.xml`)

Para visualizar o `traceId` no console em ambiente de desenvolvimento, configure o padrão de log. Para produção, configure a saída em formato **JSON** (usando `Logstash Logback Encoder`).

```xml
<configuration>
    <!-- Console Appender para Desenvolvimento -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - [traceId=%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Exemplo de JSON Appender para Produção (Loki/Kibana) -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <threadName/>
                <loggerName/>
                <message/>
                <mdc/> <!-- Copia todas as variáveis do MDC (como traceId) como propriedades do JSON -->
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

---

## ⚡ Propagação de TraceId em Processamentos Assíncronos

Como o MDC é baseado em `ThreadLocal`, threads filhas (usadas em `@Async`, computação paralela ou `WebClient`) não recebem o `traceId` automaticamente. Para resolver isso no Spring, decore o executor de tarefas.

### Decorador de Threads do Spring:
```kotlin
package com.ligadospalpites.infrastructure.config

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class AsyncConfig {

    @Bean
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.setTaskDecorator(MdcTaskDecorator())
        executor.initialize()
        return executor
    }
}

class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        // Captura o contexto da thread pai
        val contextMap = MDC.getCopyOfContextMap()
        return Runnable {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap)
                }
                runnable.run()
            } finally {
                MDC.clear()
            }
        }
    }
}
```

## ✍️ Logbook (Zalando) para Rastreamento HTTP Completo

Para registrar payloads HTTP (corpos de requisições e respostas) de forma estruturada e segura, a biblioteca **Zalando Logbook** é a recomendação padrão. Ela integra-se ao Spring Boot e garante que o payload HTTP seja logado correlacionando-o com o `traceId` correspondente da requisição.

### 1. Dependência (`build.gradle.kts`):
```kotlin
implementation("org.zalando:logbook-spring-boot-starter:<versao-compativel>") // Use a versão compatível com seu Spring Boot 4.1.0+
```

### 2. Configuração básica (`application.yml`):
O Logbook permite filtrar caminhos, mascarar headers confidenciais (ex: `Authorization`) e ocultar campos sensíveis no JSON do corpo (como `password` e `token` para LGPD/PCI-DSS):
```yaml
logbook:
  filter:
    enabled: true
  secure: true
  obfuscate:
    headers:
      - "Authorization"
      - "Cookie"
    parameters:
      - "password"
      - "client_secret"
```

---

## ✍️ Melhores Práticas para Escrita de Logs em Kotlin

### LoggerFactory Estático vs. kotlin-logging

No Java clássico, a convenção padrão é declarar um logger estático por classe:
```java
private static final Logger log = LoggerFactory.getLogger(PalpiteService.class);
```

Em Kotlin, a declaração equivalente do `LoggerFactory` tradicional envolve algumas escolhas com diferentes trade-offs:

#### Opção A: Dentro de um `companion object` (Abordagem Tradicional)
```kotlin
class PalpiteService {
    companion object {
        private val log = LoggerFactory.getLogger(PalpiteService::class.java)
    }
}
```
*   **Prós**: Comportamento idêntico ao `static final` do Java (apenas uma instância de logger por classe).
*   **Contras**: Verboso e cria um objeto companheiro (`MyClass$Companion`) em tempo de execução para cada classe, gerando overhead desnecessário de memória/bytecode.

#### Opção B: Top-level no arquivo
```kotlin
private val log = LoggerFactory.getLogger(PalpiteService::class.java)

class PalpiteService {
    fun fazerAlgo() {
        log.info("Processando...")
    }
}
```
*   **Prós**: Evita o overhead do `companion object`.
*   **Contras**: Polui o escopo do arquivo se houver mais de uma classe no mesmo arquivo.

---

### A Vantagem de usar `io.github.oshai:kotlin-logging`

A biblioteca `kotlin-logging` serve como um wrapper idiomático em cima do SLF4J, resolvendo as desvantagens acima e trazendo dois grandes benefícios:

1. **Sintaxe Limpa e Sem Companion Object**:
   ```kotlin
   private val log = KotlinLogging.logger {} // Define o logger no escopo correto automaticamente
   
   class PalpiteService {
       fun processar() {
           log.info { "Iniciando processamento" }
       }
   }
   ```
2. **Execução Tardia (Lazy Evaluation) Nativa**:
   *   **Sem Lambdas (LoggerFactory clássico)**:
       ```kotlin
       log.debug("Resultado do cálculo complexo: " + calcularDados())
       ```
       O método `calcularDados()` e a concatenação de strings serão executados **mesmo se o nível DEBUG estiver desabilitado** no ambiente (desperdício de processamento). Para evitar isso no SLF4J convencional, é necessário adicionar blocos condicionais verbosos:
       ```kotlin
       if (log.isDebugEnabled) {
           log.debug("Resultado: {}", calcularDados())
       }
       ```
   *   **Com Lambdas (Kotlin-logging)**:
       ```kotlin
       log.debug { "Resultado: ${calcularDados()}" }
       ```
       O bloco lambda `{}` **só será avaliado/executado** se o nível `DEBUG` estiver ativado no sistema, protegendo a performance do monólito sem poluir o código.

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Esquecer de limpar o MDC**: Usar `MDC.put()` sem remover em um bloco `finally` pode misturar rastreamento de requisições diferentes no log.
- ❌ **`System.out.println` ou `printStackTrace`**: Sempre use um Logger. Prints convencionais não passam pela formatação do logback e não carregam metadados (timestamp, severidade, traceId).
- ❌ **Logar Objetos de Domínio Inteiros**: Logar objetos pesados ou com referências circulares que podem causar estouro de memória ou carregar dados sob demanda (Lazy Loading). Prefira registrar IDs e propriedades específicas relevantes.

