# Spec-0003: Hybrid Data Strategy, SQL Schemas, & Leaderboards

This specification details the SQL table structures, the mapping strategy between Firebase Auth UIDs, the Redis Sorted Sets leaderboard layout, and the asynchronous event processing (Observer pattern) for calculating predictions and updating ranking cache, targeting **Spring Boot 4.1.0**.

---

## 1. Relational Database Schema (PostgreSQL DDL)

We use Postgres schema-level prefixing (using the `tbl_` prefix) to enforce bounded context boundaries.

```sql
-- Context: users
CREATE TABLE tbl_users (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_users_firebase_uid ON tbl_users(firebase_uid);

-- Context: sports-feed
CREATE TABLE tbl_matches (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL,
    league_id UUID NOT NULL,
    home_team_name VARCHAR(150) NOT NULL,
    away_team_name VARCHAR(150) NOT NULL,
    kickoff_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL, -- e.g., SCHEDULED, LIVE, FINISHED, CANCELLED
    home_score INTEGER,
    away_score INTEGER,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_matches_status ON tbl_matches(status);
CREATE INDEX idx_matches_kickoff ON tbl_matches(kickoff_time);

-- Context: predictions
CREATE TABLE tbl_predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id),
    match_id UUID NOT NULL REFERENCES tbl_matches(id),
    league_id UUID NOT NULL,
    predicted_home_score INTEGER NOT NULL,
    predicted_away_score INTEGER NOT NULL,
    points_awarded INTEGER DEFAULT 0 NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE,
    is_processed BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_match_prediction UNIQUE (user_id, match_id)
);
CREATE INDEX idx_predictions_unprocessed ON tbl_predictions(is_processed) WHERE is_processed = FALSE;

-- Context: predictions (competition leagues groups)
CREATE TABLE tbl_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    creator_id UUID NOT NULL REFERENCES tbl_users(id),
    scoring_rules_json JSONB NOT NULL, -- Config rules (exactMatchPoints, winnerPoints, etc.)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE tbl_group_members (
    group_id UUID NOT NULL REFERENCES tbl_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accumulated_points INTEGER DEFAULT 0 NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_group_members_points ON tbl_group_members(group_id, accumulated_points DESC);
```

---

## 2. Firebase User ID Mapping (Lazy Registration Pattern)

Upon a REST API call, the mobile app supplies a JWT. The backend resolves identity stateless:

```kotlin
@Component
class UserResolver(private val userRepository: UserRepository) {

    @Transactional
    fun resolve(jwtPrincipal: Jwt): UserEntity {
        val firebaseUid = jwtPrincipal.subject // claim 'sub'
        val email = jwtPrincipal.getClaimAsString("email") ?: ""
        val name = jwtPrincipal.getClaimAsString("name") ?: "Anonymous"

        return userRepository.findByFirebaseUid(firebaseUid) 
            ?: userRepository.save(
                UserEntity(
                    id = UUID.randomUUID(),
                    firebaseUid = firebaseUid,
                    email = email,
                    name = name
                )
            )
    }
}
```

---

## 3. High-Performance Rankings Layout in Redis

To avoid massive `SUM()` joins over SQL tables, active leaderboards are managed using **Redis Sorted Sets (ZSET)**.

### Keys Design
- **Global Leaderboard**: `leaderboard:global`
- **Group Leaderboard**: `leaderboard:group:{groupId}`

### Members & Scores
- **Member (Value)**: User UUID (stored as `String`).
- **Score**: User's total accumulated points (stored as `Double`).

### Redis Operations Example:
```kotlin
@Repository
class RedisLeaderboardRepository(private val redisTemplate: StringRedisTemplate) {

    fun incrementScore(leaderboardKey: String, userId: UUID, points: Int) {
        redisTemplate.opsForZSet().incrementScore(leaderboardKey, userId.toString(), points.toDouble())
    }

    fun getTopUsers(leaderboardKey: String, limit: Long): Set<ZSetOperations.TypedTuple<String>> {
        return redisTemplate.opsForZSet().reverseRangeWithScores(leaderboardKey, 0, limit - 1) ?: emptySet()
    }

    fun getUserRankAndScore(leaderboardKey: String, userId: UUID): Pair<Long?, Double?> {
        val rankZeroBased = redisTemplate.opsForZSet().reverseRank(leaderboardKey, userId.toString())
        val score = redisTemplate.opsForZSet().score(leaderboardKey, userId.toString())
        val rankOneBased = rankZeroBased?.let { it + 1 }
        return Pair(rankOneBased, score)
    }
}
```

---

## 4. Asynchronous Observer for Leaderboard Updates

We implement eventual consistency to decouple the database transaction of scoring predictions from the cache write of rankings.

```
┌─────────────────────────────────┐
│     ScoreMatchUseCase           │
│  (Database Updates & Scoring)    │
└────────────────┬────────────────┘
                 │
                 │ 1. Publishes Domain Event
                 ▼
┌─────────────────────────────────┐
│    PredictionsProcessedEvent    │
└────────────────┬────────────────┘
                 │
                 │ 2. Dispatches asynchronously
                 ▼
┌─────────────────────────────────┐
│  LeaderboardUpdaterObserver     │
│    - Update Redis ZSETs (Async) │
│    - Sync local Postgres        │
└─────────────────────────────────┘
```

### Event & Observer Implementation

```kotlin
package com.ligadospalpites.feature.predictions.application.usecases

import java.util.UUID

data class UserScoreUpdateDto(val userId: UUID, val pointsGained: Int)

data class PredictionsProcessedEvent(
    val leagueId: UUID,
    val scores: List<UserScoreUpdateDto>
)
```

```kotlin
package com.ligadospalpites.feature.predictions.infrastructure.messaging

import com.ligadospalpites.feature.predictions.application.usecases.PredictionsProcessedEvent
import com.ligadospalpites.feature.predictions.infrastructure.persistence.GroupMemberRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LeaderboardUpdaterObserver(
    private val leaderboardRepository: RedisLeaderboardRepository,
    private val groupMemberRepository: GroupMemberRepository
) {

    @Async // Run asynchronously on a background thread pool
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // Run ONLY after main DB transaction commits
    fun onPredictionsProcessed(event: PredictionsProcessedEvent) {
        val globalKey = "leaderboard:global"
        val groupKey = "leaderboard:group:${event.leagueId}"

        event.scores.forEach { update ->
            // 1. Update Redis rankings
            leaderboardRepository.incrementScore(globalKey, update.userId, update.pointsGained)
            leaderboardRepository.incrementScore(groupKey, update.userId, update.pointsGained)

            // 2. Increment database persistent counter
            groupMemberRepository.incrementUserPoints(event.leagueId, update.userId, update.pointsGained)
        }
    }
}
```
*Note: Make sure `@EnableAsync` is active in the main Spring boot application configuration class.*
