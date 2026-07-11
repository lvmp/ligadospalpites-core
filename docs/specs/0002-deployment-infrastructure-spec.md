# Spec-0002: Serverless Cloud Deployment Config

This specification details the configurations required to package, containerize, and deploy the modular monolith backend to **Google Cloud Run** using serverless instances of **Neon** and **Upstash**, running on **Spring Boot 4.1.0** and **Java 17/21**.

---

## 1. Multi-Stage Dockerfile (`Dockerfile`)

To keep build sizes minimal and optimized for Cloud Run startup times, use a multi-stage Gradle builder and a JRE runner. Place this in the project root or main app module:

```dockerfile
# Stage 1: Build the application
FROM gradle:8.5-jdk17-alpine AS builder
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN ./gradlew :apps:main-app:build -x test --no-daemon

# Stage 2: Ephemeral runtime environment
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user setup for security compliance
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/apps/main-app/build/libs/main-app-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## 2. Google Cloud Run Service Properties

To stay within the **GCP Free Tier** limits and prevent Neon Postgres connection pool starvation, apply the following service configurations in the Cloud Run service console:

| Property | Config Value | Rationale |
| :--- | :--- | :--- |
| **Max Instances** | `3` | Prevents the system from autoscaling too wide, safeguarding Neon’s free tier connection limits (max 10-20 connections). |
| **Min Instances** | `0` | Essential for **Scale-to-Zero**, resulting in $0 cost when idle. |
| **Concurrency** | `80` | Number of concurrent requests a single instance can handle before scaling up. |
| **CPU Allocation** | *Only during request processing* | GCP only charges when requests are active, keeping idle cost at exactly $0. |
| **Memory Allocation**| `512 MiB` | Sufficient for lightweight Spring Boot JVM. |
| **CPU Limit** | `1 vCPU` | Sufficient for standard throughput. |

---

## 3. Production Profile Properties (`application-prod.yml`)

Configure the production-grade parameters to hook into Neon's PgBouncer pooler and Upstash's secure Redis port:

```yaml
spring:
  config:
    activate:
      on-profile: prod

  datasource:
    # Always use the pooled connection string (port 5432, -pooler suffix) provided by Neon
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 4
      minimum-idle: 1
      idle-timeout: 30000
      connection-timeout: 20000
      max-lifetime: 1800000

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      ssl:
        enabled: true
      client-type: lettuce
      lettuce:
        shutdown-timeout: 100ms

# Production optimization properties
server:
  port: 8080
  shutdown: graceful # Allow active requests to finish before container stops
```
