---
name: integracao_dados_hibrida
description: Diretrizes para integrar Firebase (Auth, FCM), PostgreSQL (Supabase) e Redis (Upstash) de forma assíncrona com Spring Boot e Kotlin.
---

# Integração de Dados Híbrida: Firebase + PostgreSQL + Redis

Esta skill orienta o agente sobre como gerenciar dados na arquitetura híbrida do projeto **Liga dos Palpites**, garantindo alta performance, consistência eventual e custo zero no ecossistema serverless (Cloud Run + Supabase + Upstash).

---

## 🔐 1. Firebase Authentication & Spring Security
O Firebase Auth é a fonte da verdade para identidades. O backend em Spring Boot atua estritamente como um **Resource Server** stateless.

### Diretrizes para o Agente:
* **Validação de Token**: A aplicação móvel envia o Token ID do Firebase (JWT) no header `Authorization: Bearer <TOKEN>`.
* **Extração Direta de Claims**: Evite realizar chamadas remota síncronas com o SDK Admin (`firebaseAuth.getUser()`) dentro do fluxo HTTP principal. Extraia dados como e-mail e nome diretamente das claims do JWT (`jwt.getClaimAsString("email")`, `name`, `picture`).
* **Mapeamento de Usuário no Postgres**:
  * Ao receber uma requisição autenticada, capture a claim `sub` (Firebase UID).
  * Salve este UID na tabela local `tbl_users` como chave primária ou índice único (`firebase_uid VARCHAR(128)`).

---

## 📊 2. Estrutura de Rankings com Redis Sorted Sets (ZSET)
Evite a todo custo realizar consultas com `SUM()` e `GROUP BY` no PostgreSQL para exibir tabelas de classificação (leaderboards), e nunca utilize o Firestore para esta finalidade devido ao custo por leitura/escrita. Utilize o **Redis Sorted Sets (ZSET)**.

### Operações em Lote com Redis Pipelines (`executePipelined`):
Para atualizações em massa (ex: ao encerrar uma partida com centenas de palpites), **sempre utilize Redis Pipelines** para reduzir IOPS e eliminar latência de múltiplos RTTs de rede:

```kotlin
fun updateLeaderboardScoresPipelined(updates: List<ScoreUpdateItem>) {
    redisTemplate.executePipelined { connection ->
        val stringConn = connection.stringCommands()
        updates.forEach { item ->
            stringConn.zIncrBy(
                item.key.toByteArray(),
                item.pointsGained.toDouble(),
                item.userId.toString().toByteArray()
            )
        }
        null
    }
}
```

---

## 🔄 3. Processamento Assíncrono com Observer Pattern (Eventos)
A escrita na base operacional PostgreSQL (salvar palpites dos usuários e resultados dos jogos) deve ser mantida rápida. A atualização de classificações no Redis e o disparo de pushes (FCM) devem ocorrer em segundo plano, utilizando o padrão **Observer** nativo do Spring (`@TransactionalEventListener`).

### Agrupamento de Updates Relacionais (Evitando N+1):
Ao atualizar os pontos acumulados nas tabelas relacionais (como `tbl_group_members`), não faça buscas e atualizações unitárias em loop. Busque os membros afetados em lote (`findByUserIdIn`) e execute atualizações de banco em instruções SQL agrupadas.

---

## ⚠️ O que EVITAR (Anti-patterns)

* ❌ **Executar ZINCRBY unitários em loops no Redis**: Disparar comandos isolados de ZSET no Redis para cada palpite de usuário. Use `executePipelined`.
* ❌ **Fazer chamadas síncronas à API do Firebase Auth**: Consultar o SDK Admin do Firebase Auth durante cada requisição REST adiciona de 200 a 500ms de latência externa. Use as claims contidas no JWT.
* ❌ **Atualizar o Redis dentro da transação do Postgres**: Se a rede falhar ao conectar ao Redis ou o Upstash atingir o limite de conexões, a transação do banco principal seria revertida (Rollback). Sempre separe a consistência do cache usando eventos pós-commit.
* ❌ **Confiar cegamente no estado do Redis como Fonte da Verdade primária**: Redis ZSET é para leitura de alta performance. Os pontos acumulados individuais de cada palpite de usuário **devem estar gravados no PostgreSQL** (`tbl_predictions.points_award`).

