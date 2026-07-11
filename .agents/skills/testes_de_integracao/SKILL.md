---
name: testes_de_integracao
description: Diretrizes para escrita de testes de integração com Kotlin, Spring Boot, Testcontainers e Docker Compose.
---

# Testes de Integração com Docker & Testcontainers

Esta skill orienta o agente na configuração e escrita de testes de integração no projeto **Liga dos Palpites** utilizando **Docker** e **Testcontainers** integrado ao Spring Boot 4.x (estável na versão 4.1.0) com Kotlin.

---

## 🛠️ Princípios Fundamentais

1. **Isolamento de Banco de Dados**: Cada conjunto de testes de integração deve interagir com instâncias isoladas e limpas de PostgreSQL e Redis executadas via Docker.
2. **Sem Credenciais Hardcoded**: Configurações de banco de dados para testes não devem conter URIs, portas ou senhas estáticas. A injeção de conexões deve ocorrer dinamicamente via Testcontainers.
3. **Reutilização de Containers**: Para acelerar os testes no Gradle, instâncias de container devem ser compartilhadas entre classes de testes sempre que possível, evitando a inicialização excessiva de containers.

---

## 💻 Configuração do Ambiente Local (Docker Compose)

O arquivo `docker-compose.yml` na raiz do projeto gerencia os serviços necessários para rodar a aplicação localmente (`local` profile). 
A dependência `spring-boot-docker-compose` gerencia o ciclo de vida deste compose automaticamente.

### Diretriz para o Agente:
* **Não inicialize o banco manualmente**: Quando rodar a aplicação com o perfil local (e.g., usando o comando do Gradle `./gradlew :apps:main-app:bootRun`), deixe que o próprio Spring Boot suba os containers descritos no `docker-compose.yml`.
* **Configuração de desligamento**: O arquivo de configuração da aplicação local deve conter a propriedade para manter os containers ativos ou pará-los de acordo com o desejado:
  ```yaml
  spring:
    docker:
      compose:
        lifecycle-management: start_and_stop
        readiness:
          tcp:
            connect-timeout: 10s
  ```

---

## 🧪 Escrita de Testes com Testcontainers

### 1. Conexões Dinâmicas com `@ServiceConnection`
Desde o Spring Boot 3.1+ (e totalmente compatível e recomendado no Spring Boot 4.1.0+), a anotação `@ServiceConnection` elimina a necessidade de registrar propriedades manualmente (via `DynamicPropertyRegistry`). O Spring Boot detecta automaticamente a imagem do container e injeta as credenciais corretas no contexto da aplicação.

#### Padrão de Classe de Teste Unitária/Integração Isolada:
```kotlin
package com.ligadospalpites.feature.predictions.infrastructure.persistence

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PalpiteJpaRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @ServiceConnection
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Test
    fun `should persist and retrieve entity successfully`() {
        // Lógica de teste
    }
}
```

### 2. Padrão Recomendado: Classe Base Abstrata para Testes Reutilizáveis
Para evitar inicializar novos containers PostgreSQL e Redis para **cada** classe de teste (o que torna a compilação lenta), defina uma classe base abstrata para compartilhar a mesma instância de container por toda a suite de testes.

```kotlin
package com.ligadospalpites.shared

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseIntegrationTest {

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            start()
        }

        val redis = GenericContainer("redis:7-alpine").withExposedPorts(6379).apply {
            start()
        }

        // Registro de propriedades dinâmicas caso as conexões de serviço não sejam automáticas
        @org.springframework.test.context.DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
```

*Qualquer classe de teste de integração que herde de `BaseIntegrationTest` usará os mesmos containers de banco e cache já inicializados.*

---

## ⚠️ O que EVITAR (Anti-patterns)

* ❌ **Executar testes contra bancos locais/estáticos**: Configurar o `src/test/resources/application.yml` apontando para `localhost:5432` com credenciais fixas. Isso quebra builds em servidores de CI (GitHub Actions) que não têm esses bancos locais rodando na porta padrão.
* ❌ **Esquecer de limpar dados entre os testes**: Os testes devem ser transacionais (anotados com `@Transactional`) ou ter métodos `@AfterEach` que limpam as tabelas do PostgreSQL e limpam o Redis (`redisTemplate.connectionFactory.connection.serverCommands().flushDb()`) para evitar que dados de um teste afetem o comportamento de outro.
* ❌ **Não definir timeout de readiness**: Containers podem demorar para iniciar no Docker. Sempre garanta que os containers tenham tempo hábil para iniciar, definindo limites razoáveis de startup.
