---
name: resiliencia_e_rate_limiting
description: Diretrizes e configurações para integração de APIs externas resilientes usando Resilience4j (Circuit Breakers, Retries) e controle de rate-limiting no Spring Boot 4.1.0 e Kotlin.
---

# Resiliência e Controle de Rate Limiting para APIs Externas

Esta skill define os padrões arquiteturais para consumo seguro de APIs externas (como API-Sports e NewsAPI), evitando o bloqueio de threads locais, lidando com cotas limites de requisições gratuitas e aplicando técnicas de degradação graciosa (*graceful degradation*).

---

## 1. Configuração de Timeouts em Clientes HTTP

Nunca use as configurações padrão de HTTP Clients do Spring, pois elas possuem timeouts infinitos. Sempre declare timeouts explícitos de conexão e leitura:

```kotlin
@Configuration
class HttpClientConfig {

    @Bean
    fun restClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000) // 5 segundos max para estabelecer conexão TCP
            setReadTimeout(10000)   // 10 segundos max para aguardar pacotes de resposta
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
```

---

## 2. Padrões de Resiliência com Resilience4j

Utilize **Circuit Breakers** e **Retries** da biblioteca Resilience4j para isolar falhas de APIs externas e garantir que a aplicação continue de pé.

### A. Exemplo Prático em Kotlin com Degradação Graciosa (Fallback)
Ao falhar a busca na API externa (por limite de taxa ou indisponibilidade), a aplicação deve retornar dados em cache ou informações parciais do banco local para evitar erro ao usuário final.

```kotlin
@Service
class SportsFeedService(
    private val restClient: RestClient,
    private val matchRepository: LocalMatchRepository
) {

    @CircuitBreaker(name = "sportsApi", fallbackMethod = "fetchMatchesFallback")
    @Retry(name = "sportsApi")
    fun fetchLiveMatchesFromApi(): List<MatchDto> {
        val response = restClient.get()
            .uri("https://v3.football.api-sports.io/fixtures?live=all")
            .header("x-apisports-key", "my-key")
            .retrieve()
            .body(ApiSportsResponseDto::class.java)
            
        return response?.toDomainList() ?: emptyList()
    }

    // Método Fallback acionado se a API cair ou o Circuito abrir
    fun fetchMatchesFallback(exception: Throwable): List<MatchDto> {
        // Degradação graciosa: Busca partidas ativas salvas no banco de dados local
        logger.warn("External Sports API failed. Serving from local database fallback. Error: ${exception.message}")
        return matchRepository.findAllActiveMatches().map { it.toDto() }
    }
}
```

---

## 3. Configurações recomendadas no application.yml

As seguintes configurações definem o comportamento do circuito de proteção:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      sportsApi:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10          # Avalia o status a cada 10 requisições
        failureRateThreshold: 50       # Abre o circuito se 50% ou mais das chamadas falharem
        slowCallRateThreshold: 50      # Abre o circuito se 50% das chamadas forem lentas
        slowCallDurationThreshold: 5s  # Chamadas com mais de 5s são consideradas lentas
        waitDurationInOpenState: 30s   # Aguarda 30 segundos no estado Aberto antes de tentar Half-Open
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      sportsApi:
        maxAttempts: 3                 # Tenta no máximo 3 vezes antes de desistir
        waitDuration: 2s               # Aguarda 2 segundos entre as tentativas
        exponentialBackoff:            # Multiplicador exponencial de tempo
          enabled: true
          multiplier: 2
```

---

## 4. Gerenciamento de Cotas (Rate Limiting) de Provedores

A API-Sports e a NewsAPI possuem limites de chamadas diárias e por minuto (ex: 1 requisição por segundo no tier grátis do NewsAPI).

### Boas Práticas:
1. **Evite Chamadas Redundantes**: Armazene em cache por tempo razoável (ex. notícias cacheadas por 1 hora; tabela de standings agregada por 15 minutos).
2. **RateLimiter Local**: Utilize a anotação `@RateLimiter(name = "sportsApiLimit")` se o agendador externo fizer disparos em alta frequência, impedindo que a aplicação faça requisições excedentes às cotas das APIs externas.
3. **Paciência no Tratamento de Erros**: Trate explicitamente o erro HTTP `429 Too Many Requests`. Se recebido, ative temporariamente uma flag de suspensão curta para evitar novas tentativas imediatas que queimem conexões.
