---
name: banco_de_dados_e_migrations_flyway
description: Diretrizes para gerenciamento de schema relacional com Flyway migrations no Supabase PostgreSQL e separação de responsabilidades com Upstash Redis.
---

# 🗄️ Banco de Dados, Migrations com Flyway e Separação de Dados

Esta skill orienta a evolução do banco de dados relacional PostgreSQL (Supabase) e a convivência arquitetural com a camada de cache/leaderboards em Redis (Upstash).

---

## 📜 1. Versionamento de Schema com Flyway

- **Padrão de Nomeação**: Todas as alterações na estrutura do PostgreSQL devem ser feitas estritamente via scripts de migration do Flyway em `src/main/resources/db/migration/`.
  - Exemplo: `V1__create_users_table.sql`, `V2__create_notifications_table.sql`.
- **Imutabilidade**: Scripts de migration aplicados nunca devem ser alterados localmente. Novas correções exigem uma nova migration (ex: `V3__add_index_to_notifications.sql`).
- **Idempotência e Segurança**: Assegure-se de definir tipos de dados, chaves primárias (`UUID` ou `BIGINT`), constraints de foreign keys e índices adequados para consultas frequentes.

---

## ⚡ 2. Divisão Relacional (Supabase) vs Redis (Upstash)

- **PostgreSQL (Supabase)**:
  - Armazena o estado persistente do sistema: usuários, palpites, notificações em feed in-app, histórico de compras/assinaturas e partidas.
- **Redis (Upstash)**:
  - Armazena pontuações em tempo real e leaderboards/rankings via **Sorted Sets (ZSET)**.
  - Armazena cache de curta/média duração de endpoints de leitura pesada.
  - **Atenção**: O Firestore **não** deve ser utilizado para dados operacionais ou leaderboards.

---

## 🧪 3. Testes de Integração de Banco de Dados

- Todos os testes que interagem com o banco relacional devem utilizar o **Testcontainers** rodando a imagem oficial do PostgreSQL, garantindo que as migrations do Flyway sejam executadas do zero antes dos testes.
- Herdando de `BaseIntegrationTest` com a anotação `@ServiceConnection` do Spring Boot.
