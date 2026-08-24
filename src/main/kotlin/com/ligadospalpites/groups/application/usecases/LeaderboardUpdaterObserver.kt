package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
import com.ligadospalpites.groups.infrastructure.persistence.ScoreIncrementItem
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LeaderboardUpdaterObserver(
    private val leaderboardRepository: RedisLeaderboardRepository,
    private val springDataGroupMemberRepository: SpringDataGroupMemberRepository
) {
    private val log = LoggerFactory.getLogger(LeaderboardUpdaterObserver::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPredictionsProcessed(event: PredictionsProcessedEvent) {
        if (event.scores.isEmpty()) return

        log.info("LeaderboardUpdaterObserver processing batch of ${event.scores.size} scores for league ${event.leagueId}")
        val globalKey = "leaderboard:global"
        val leagueKey = "leaderboard:league:${event.leagueId}"

        val redisUpdates = mutableListOf<ScoreIncrementItem>()
        val userIds = event.scores.map { it.userId }.toSet()
        val userPointsMap = event.scores.associate { it.userId to it.pointsGained }

        // 1. Prepara atualizações para Global e Liga no Redis
        event.scores.forEach { update ->
            redisUpdates.add(ScoreIncrementItem(globalKey, update.userId, update.pointsGained))
            redisUpdates.add(ScoreIncrementItem(leagueKey, update.userId, update.pointsGained))
        }

        // 2. Busca todos os grupos dos usuários em uma única query SQL (Batch)
        val memberships = springDataGroupMemberRepository.findByUserIdIn(userIds)
        log.info("Found ${memberships.size} group memberships to update in Postgres for batch")

        // 3. Atualiza Postgres e agrupa atualizações dos ZSETs dos grupos no Redis
        memberships.forEach { membership ->
            val pointsGained = userPointsMap[membership.userId] ?: 0
            if (pointsGained > 0) {
                springDataGroupMemberRepository.incrementUserPoints(membership.groupId, membership.userId, pointsGained)

                val groupId = membership.groupId
                redisUpdates.add(ScoreIncrementItem("leaderboard:group:$groupId:overall", membership.userId, pointsGained))
                redisUpdates.add(ScoreIncrementItem("leaderboard:group:$groupId:group-stage", membership.userId, pointsGained))
                redisUpdates.add(ScoreIncrementItem("leaderboard:group:$groupId:knockout", membership.userId, pointsGained))
            }
        }

        // 4. Executa todas as atualizações no Redis em um ÚNICO pacote Pipelined (1 RTT de rede)
        log.info("Executing ${redisUpdates.size} ZSET score updates via Redis Pipeline")
        leaderboardRepository.incrementScoresPipelined(redisUpdates)
    }
}
