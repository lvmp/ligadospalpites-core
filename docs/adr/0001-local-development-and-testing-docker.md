# ADR-0001: Local Development and Testing Environment with Docker & Testcontainers

## Status
Accepted

## Date
2026-07-10

## Context
The **Liga dos Palpites** core backend is built as a modular monolith using Kotlin and Spring Boot, depending on two primary external services: **PostgreSQL** (for persistence) and **Redis** (for caching and real-time leaderboards). 

In traditional environments, setting up these services manually on developer machines leads to:
1. **Developer Friction**: Installing, configuring, and running PostgreSQL/Redis locally.
2. **Environment Drift**: Inconsistencies in database versions, schemas, and configurations across different machines.
3. **Flaky Tests**: Running integration tests against shared, persistent databases where leftover test data causes test pollution and failures.

To address these challenges, we need a standardized, reproducible, and isolated local development and testing setup.

## Decision
We will standardize our local execution and testing workflows around **Docker** and **Testcontainers**, leveraging native Spring Boot integration.

### 1. Local Development (Docker Compose)
We will maintain a standard `docker-compose.yml` in the root of the project to declare local instances of:
- **PostgreSQL**: Version 16+ (matching production).
- **Redis**: Version 7+ (matching production).

Furthermore, we will include the `org.springframework.boot:spring-boot-docker-compose` dependency in the development scope. When running the application locally (e.g., via `./gradlew :apps:main-app:bootRun`):
- Spring Boot will automatically detect the `docker-compose.yml` file.
- It will invoke `docker compose up` at application startup.
- It will automatically resolve and inject database URLs, usernames, passwords, and Redis connection strings into the Spring Context.
- It will invoke `docker compose down` when the application stops.

### 2. Integration Testing (Testcontainers)
For repository, use-case, and controller integration tests (annotated with `@SpringBootTest`), we will enforce the use of **Testcontainers**:
- Test suites requiring database access will spin up isolated, ephemeral PostgreSQL and Redis containers.
- We will leverage Spring Boot's `@ServiceConnection` annotation (available since Spring Boot 3.1) to automatically configure connection details from the Testcontainer to the Spring Application Context.

Example test setup:
```kotlin
@SpringBootTest
@Testcontainers
class PredictionsIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @ServiceConnection
        val redis = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Test
    fun `should submit palpite and persist to database`() {
        // Test logic...
    }
}
```

## Consequences

### Positive (Benefits)
* **Zero Configuration**: A developer only needs a running Docker daemon (Docker Desktop, Rancher Desktop, or Colima) to compile, test, and run the project. No local database installation is needed.
* **Isolated Testing**: Every integration test runs against a clean, dedicated database instance, eliminating side-effects and test pollution.
* **Parity with Production**: Ephemeral containers run the exact same versions of PostgreSQL and Redis as target cloud environments.

### Negative (Trade-offs)
* **Startup Overhead**: Test containers add a small delay (typically 3 to 10 seconds) during the initial test suite execution as Docker pulls images and starts container daemons.
* **System Requirements**: Running Docker containers locally requires more memory and CPU allocation on development machines.
