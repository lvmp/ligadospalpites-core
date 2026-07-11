---
name: integracao_dados_hibrida
description: Diretrizes para integrar Firebase (Auth, FCM), PostgreSQL (Neon) e Redis (Upstash) de forma assíncrona com Spring Boot e Kotlin.
---

# Integração de Dados Híbrida: Firebase + PostgreSQL + Redis

Esta skill orienta o agente sobre como gerenciar dados na arquitetura híbrida do projeto **Liga dos Palpites**, garantindo alta performance, consistência eventual e custo zero no ecossistema serverless (Cloud Run + Neon + Upstash).

---

## 🔐 1. Firebase Authentication & Spring Security
O Firebase Auth é a fonte da verdade para identidades. O backend em Spring Boot atua estritamente como um **Resource Server** stateless.

### Diretrizes para o Agente:
* **Validação de Token**: A aplicação móvel envia o Token ID do Firebase (JWT) no header `Authorization: Bearer <TOKEN>`.
* **Spring Security Configuration**: Configure o Spring Security para validar o JWT decodificado por meio do emissor do Firebase (`https://securetoken.google.com/<firebase-project-id>`).
* **Mapeamento de Usuário no Postgres**:
  * Ao receber uma requisição autenticada, capture a claim `sub` (Firebase UID).
  * Salve este UID na tabela local `tbl_users` como chave primária ou índice único (`firebase_uid VARCHAR(128)`).
  * Exemplo de resolução de usuário local (Lazy Registration):
    ```kotlin
    fun resolveUser(firebaseUid: String, email: String, name: String): User {
        return userRepository.findByFirebaseUid(firebaseUid)
            ?: userRepository.save(User(id = UUID.randomUUID(), firebaseUid = firebaseUid, email = email, name = name))
    }
    ```

---

## 📊 2. Estrutura de Rankings com Redis Sorted Sets (ZSET)
Evite a todo custo realizar consultas com `SUM()` e `GROUP BY` no PostgreSQL para exibir tabelas de classificação (leaderboards), e nunca utilize o Firestore para esta finalidade devido ao custo por leitura/escrita. Utilize o **Redis Sorted Sets (ZSET)**.

### Comandos Redis Recomendados via Kotlin (`StringRedisTemplate`):

1. **Adicionar ou Atualizar Pontuação do Usuário**:
   Use a operação `add` ou `incrementScore`.
   ```kotlin
   val globalKey = "leaderboard:global"
   val groupKey = "leaderboard:group:${groupId}"
   
   // Incrementa a pontuação acumulada do usuário
   redisTemplate.opsForZSet().incrementScore(globalKey, userId.toString(), pointsGained.toDouble())
   redisTemplate.opsForZSet().incrementScore(groupKey, userId.toString(), pointsGained.toDouble())
   ```

2. **Obter os Top 10 Usuários (Página de Rankings)**:
   ```kotlin
   val topPlayers = redisTemplate.opsForZSet().reverseRangeWithScores(globalKey, 0, 9)
   // Retorna um Set de TypedTuple contendo userId e score de forma ordenada decrescente.
   ```

3. **Obter a Colocação Relativa e Pontuação de um Usuário Específico**:
   ```kotlin
   val zeroBasedRank = redisTemplate.opsForZSet().reverseRank(globalKey, userId.toString())
   val score = redisTemplate.opsForZSet().score(globalKey, userId.toString())
   
   val actualRank = (zeroBasedRank ?: 0) + 1
   ```

---

## 🔄 3. Processamento Assíncrono com Observer Pattern (Eventos)
A escrita na base operacional PostgreSQL (salvar palpites dos usuários e resultados dos jogos) deve ser mantida rápida. A atualização de classificações no Redis e o disparo de pushes (FCM) devem ocorrer em segundo plano, utilizando o padrão **Observer** nativo do Spring.

### Implementação do Fluxo de Eventos:

1. **Definição dos Eventos de Domínio**:
   ```kotlin
   data class PredictionsProcessedEvent(
       val matchId: UUID,
       val leagueId: UUID,
       val userPoints: List<UserPointsDto>
   )
   
   data class UserPointsDto(val userId: UUID, val pointsGained: Int)
   ```

2. **Publicação do Evento no Use Case**:
   ```kotlin
   @Service
   class ScoreMatchUseCase(
       private val eventPublisher: ApplicationEventPublisher,
       private val matchRepository: MatchRepository
   ) {
       @Transactional
       fun execute(matchId: MatchId, score: Score) {
           // 1. Atualiza resultado no banco Postgres
           // 2. Calcula pontos de todos os palpites no Postgres
           // 3. Reúne a lista de pontuações geradas
           
           eventPublisher.publishEvent(
               PredictionsProcessedEvent(matchId.value, leagueId.value, pointsList)
           )
       }
   }
   ```

3. **Observer Assíncrono (Atualização de Cache e Rankings)**:
   Use a anotação `@TransactionalEventListener` com a fase `AFTER_COMMIT` para garantir que o Redis só seja atualizado caso a gravação no PostgreSQL tenha sido consolidada com sucesso. Adicione `@Async` para processar em uma thread background.

   ```kotlin
   @Component
   class LeaderboardUpdaterObserver(
       private val redisTemplate: StringRedisTemplate
   ) {
       @Async
       @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
       fun handlePredictionsProcessed(event: PredictionsProcessedEvent) {
           val globalKey = "leaderboard:global"
           val groupKey = "leaderboard:group:${event.leagueId}"
           
           event.userPoints.forEach { userPoint ->
               // Atualiza o ranking global
               redisTemplate.opsForZSet().incrementScore(
                   globalKey, 
                   userPoint.userId.toString(), 
                   userPoint.pointsGained.toDouble()
               )
               
               // Atualiza o ranking da liga específica
               redisTemplate.opsForZSet().incrementScore(
                   groupKey, 
                   userPoint.userId.toString(), 
                   userPoint.pointsGained.toDouble()
               )
           }
       }
   }
   ```

---

## ⚠️ O que EVITAR (Anti-patterns)

* ❌ **Atualizar o Redis dentro da transação do Postgres**: Se a rede falhar ao conectar ao Redis ou o Upstash atingir o limite de conexões, a transação do banco principal seria revertida (Rollback). Sempre separe a consistência do cache usando eventos pós-commit.
* ❌ **Confiar cegamente no estado do Redis como Fonte da Verdade primária**: Redis ZSET é para leitura de alta performance. Os pontos acumulados individuais de cada palpite de usuário **devem estar gravados no PostgreSQL** (`tbl_predictions.points_award`). Se o cache Redis cair ou expirar, deve ser possível reconstruir todo o leaderboard executando um script de recriação baseado nos dados estruturados do Postgres.
* ❌ **Fazer varreduras completas no Redis (`KEYS *`)**: Se precisar obter informações de rankings ou grupos, use exclusivamente chaves estruturadas diretas ou padrões de busca eficientes (`SCAN`), pois `KEYS` bloqueia o thread único do Redis.
