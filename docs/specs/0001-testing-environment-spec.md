# Spec-0001: Local Development & Integration Testing Setup

This specification details the configuration of the local development environment and the automated testing harness using **Docker Compose** and **Testcontainers**, targeting **Spring Boot 4.1.0** and **Kotlin 1.9+**.

---

## 1. Local Development Stack (`docker-compose.yml`)

A single `docker-compose.yml` file must reside in the project root to spin up PostgreSQL and Redis.

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: ligadospalpites-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: ligadospalpites
      POSTGRES_USER: palpiteiro
      POSTGRES_PASSWORD: supersecretpassword
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U palpiteiro -d ligadospalpites"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: ligadospalpites-redis
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
  redisdata:
```

---

## 2. Gradle Dependencies (`build.gradle.kts`)

Include the following dependencies in the respective module subprojects (e.g., `apps/main-app` or shared testing packages):

```kotlin
dependencies {
    // Development Compose Support (automatically boots services during gradle bootRun)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Integration Testing & Testcontainers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
```

---

## 3. Ephemeral Test Configuration (`BaseIntegrationTest.kt`)

To prevent the Gradle compiler from starting up database containers for every single integration test class, we enforce a **Shared Ephemeral Container** pattern. Create an abstract base class `BaseIntegrationTest` in `packages/shared/core/src/test/kotlin/` or a specialized testing module:

```kotlin
package com.ligadospalpites.shared

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
abstract class BaseIntegrationTest {

    companion object {
        // Shared Postgres instance
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply {
            withDatabaseName("ligadospalpites-test")
            withUsername("testuser")
            withPassword("testpass")
            start()
        }

        // Shared Redis instance
        private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).apply {
            withExposedPorts(6379)
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Dynamically register Postgres configuration
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            // Dynamically register Redis configuration
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
```

---

## 4. Test Cleanup Strategy

To ensure test isolation without restarting containers:
1. Annotate database-writing tests with `@Transactional` so changes are rolled back automatically.
2. For cache tests, inject `StringRedisTemplate` and flush keys before or after each test execution:

```kotlin
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate

abstract class BaseIntegrationTest {
    
    @Autowired
    protected lateinit var redisTemplate: StringRedisTemplate

    @AfterEach
    fun cleanUpRedis() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }
}
```
