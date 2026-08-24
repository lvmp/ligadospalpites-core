# ADR-0018: Hybrid Data Layer Performance Optimization & Redis Pipelining

## Status
Accepted

## Date
2026-08-24

## Context
A plataforma **Liga dos Palpites** utiliza uma arquitetura de dados híbrida ([ADR-0003](0003-data-strategy-firebase-postgres.md)): PostgreSQL no Supabase para dados operacionais relacionais, Redis no Upstash para Sorted Sets (ZSET) de leaderboards/rankings e Firebase para Auth/FCM.

Conforme a base de usuários e o volume de jogos cresceu, a análise da execução revelou três gargalos na camada de integração:
1. **Latência de Rede e IOPS no Redis (ZSET Updates)**: No encerramento de cada partida, centenas de palpites são processados. O observer disparava chamadas individuais síncronas de `ZINCRBY` no Redis para o ranking global, ranking da liga e rankings de cada grupo do usuário. Em cenários de pico, isso gerava milhares de requisições HTTP/TCP isoladas ao Upstash, consumindo desnecessariamente a cota de IOPS.
2. **Gargalo N+1 no PostgreSQL**: Durante a execução do observer, o sistema executava queries `findByUserId` unitárias no Postgres e atualizações individuais `incrementUserPoints` por grupo.
3. **Instanciação Manual de Threads no BFF Gateway**: O controller principal (`DashboardController`) criava um `Executors.newFixedThreadPool(10)` nativo do Java em vez de utilizar o `TaskExecutor` gerenciado pelo Spring, prejudicando o contexto de tracing e o graceful shutdown em contêineres Serverless (Google Cloud Run).

## Decision

Decidimos aplicar um conjunto de otimizações de alta performance na camada de persistência e integração:

### 1. Redis Pipelining em Operações de Sorted Set (ZSET)
* Implementar o método `incrementScoresPipelined` no `RedisLeaderboardRepository` usando `redisTemplate.executePipelined`.
* O `LeaderboardUpdaterObserver` acumula todos os deltas de pontuação gerados por uma partida e os despacha em um único pacote de rede (RTT único).

### 2. Eliminação de N+1 Queries no PostgreSQL
* O `SpringDataGroupMemberRepository` agora consulta os relacionamentos de grupos utilizando busca em lote (`findByUserIdIn`).
* Os pontos acumulados nos grupos são incrementados de forma agrupada na transação do observer.

### 3. Padronização de Thread Pools no Spring Framework
* O `DashboardController` e serviços assíncronos passam a utilizar o `AsyncTaskExecutor` configurado e gerenciado pela fábrica do Spring Boot (`taskExecutor`), garantindo integração com MDC/Logbook e compatibilidade com o ciclo de vida do container Cloud Run.

### 4. Limpeza da Camada de Cache L1/L2
* O `L1NewsCacheService` foi simplificado para confiar no fluxo transparente da abstração `@Cacheable` do Spring, eliminando chamadas manuais repetitivas ao `StringRedisTemplate` dentro do corpo do método interceptado.

## Consequences

### Positive (Benefícios)
* **Redução de até 90% no Consumo de IOPS do Upstash**: Toda a carga de atualização de leaderboards de uma partida inteira é compactada em 1 único comando pipeline.
* **Redução de Latência no Encerramento de Partidas**: O processamento de centenas de palpites pós-jogo é reduzido de vários segundos para milissegundos.
* **Melhoria no Gerenciamento Serverless**: Eliminação de thread pools perdidos fora do ciclo de vida da aplicação Spring Boot.

### Negative (Trade-offs)
* Ligeiro aumento de memória transitória no listener durante o acúmulo de pacotes de pipeline antes do envio ao Redis.
