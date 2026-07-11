---
name: cache_e_performance_redis
description: Diretrizes de boas práticas para gerenciamento de cache de alta performance com Upstash Redis (ZSETs e caching) no Spring Boot 4.1.0 e Kotlin.
---

# Cache & Performance Redis: Diretrizes de Implementação

Esta skill orienta o desenvolvimento de rotinas de caching e rankings em tempo real utilizando **Upstash Redis** no backend **Spring Boot 4.1.0** e **Kotlin**.

---

## 1. Padrões de Operações com Sorted Sets (ZSETs)

Rankings gerais e rankings por fase (fase de grupos e mata-mata) são persistidos no Redis como **Sorted Sets** utilizando `RedisTemplate<String, String>` ou `ReactiveRedisTemplate`.

### A. Convenção de Nomenclatura de Chaves (Keys)
As chaves devem seguir o padrão:
* **Geral (Overall)**: `leaderboard:group:{groupId}:overall`
* **Fase de Grupos**: `leaderboard:group:{groupId}:group-stage`
* **Mata-Mata**: `leaderboard:group:{groupId}:knockout`

### B. Inserção / Incremento de Scores (Kotlin)
Utilize `opsForZSet().add()` ou `opsForZSet().incrementScore()` para atualizar a pontuação dos membros de forma eficiente.

```kotlin
@Component
class GroupLeaderboardRepository(private val redisTemplate: RedisTemplate<String, String>) {

    fun updateMemberScore(groupId: UUID, phase: String, userId: UUID, newScore: Double) {
        val key = "leaderboard:group:$groupId:$phase"
        // ZADD key score member
        redisTemplate.opsForZSet().add(key, userId.toString(), newScore)
    }

    fun incrementMemberScore(groupId: UUID, phase: String, userId: UUID, pointsGained: Double) {
        val key = "leaderboard:group:$groupId:$phase"
        // ZINCRBY key increment member
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), pointsGained)
    }
}
```

---

## 2. Paginação de Rankings com Alta Performance

Evite ler o ZSET completo. Retorne apenas fatias (chunks) utilizando paginação baseada em índices (Rank).

### Busca Reversa de Elementos (Mais Pontos primeiro)
A ordenação em ZSET padrão é ascendente. Para rankings, utilize sempre a operação reversa (`reverseRangeWithScores`).

```kotlin
fun getRankingsPage(groupId: UUID, phase: String, page: Int, pageSize: Int): List<RankedUserDto> {
    val key = "leaderboard:group:$groupId:$phase"
    val start = page * pageSize.toLong()
    val end = start + pageSize - 1

    // ZREVRANGE key start end WITHSCORES
    val typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end) ?: emptySet()
    
    var currentRank = start + 1
    return typedTuples.map { tuple ->
        RankedUserDto(
            rank = currentRank++,
            userId = UUID.fromString(tuple.value),
            score = tuple.score?.toInt() ?: 0
        )
    }
}
```

---

## 3. Evitando Consultas N+1 (Padrão Cache-Aside e Mappings)

Ao carregar o ranking de usuários do Redis, o retorno contém apenas a tupla `(userId, score)`. Fazer uma consulta por usuário no banco relacional ou Firebase para obter o nome e foto de perfil gera o gargalo **N+1 queries**.

### Solução Recomendada: Busca em Lote (Batching)
1. Recupere a lista de `userId` paginada do Redis.
2. Busque os metadados dos perfis (ex: `displayName`, `avatarUrl`) em lote (Query `IN` ou busca em lote no Firebase).
3. Faça o mapeamento em memória antes de retornar o DTO final.

```kotlin
fun getLeaderboardWithProfiles(groupId: UUID, phase: String, page: Int, pageSize: Int): List<LeaderboardRow> {
    val rankedUsers = getRankingsPage(groupId, phase, page, pageSize)
    if (rankedUsers.isEmpty()) return emptyList()

    val userIds = rankedUsers.map { it.userId }
    
    // Busca perfis em lote (exemplo: Firestore ou Postgres)
    val profilesMap = profileService.fetchProfilesInBatch(userIds) // Retorna Map<UUID, UserProfile>

    return rankedUsers.map { ranked ->
        val profile = profilesMap[ranked.userId]
        LeaderboardRow(
            rank = ranked.rank,
            userId = ranked.userId,
            displayName = profile?.displayName ?: "Jogador",
            avatarUrl = profile?.avatarUrl ?: "",
            score = ranked.score
        )
    }
}
```

---

## 4. Otimização de Conexões e Pipelines com Upstash

O Upstash Redis cobra por número de comandos enviados.
* **Pipelines**: Para atualizações em massa (por exemplo, após o fim de uma partida, onde milhares de palpites de usuários são processados), utilize **Redis Pipelines**. Isso envia dezenas de atualizações em um único pacote HTTP/TCP, reduzindo latência de rede e custos de IOPS.

```kotlin
fun updateScoresInPipeline(groupId: UUID, phase: String, scores: Map<UUID, Double>) {
    val key = "leaderboard:group:$groupId:$phase"
    redisTemplate.executePipelined(SessionCallback<Any?> { connection ->
        scores.forEach { (userId, score) ->
            redisTemplate.opsForZSet().add(key, userId.toString(), score)
        }
        null
    })
}
```
