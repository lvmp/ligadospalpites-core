package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LeaderboardUpdaterObserver(
    private val leaderboardRepository: RedisLeaderboardRepository,
    private val springDataGroupMemberRepository: SpringDataGroupMemberRepository
) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPredictionsProcessed(event: PredictionsProcessedEvent) {
        val globalKey = "leaderboard:global"

        event.scores.forEach { update ->
            // 1. Atualiza ranking global no Redis
            leaderboardRepository.incrementScore(globalKey, update.userId, update.pointsGained)

            // 2. Busca todos os grupos aos quais este usuário pertence no PostgreSQL
            val userGroups = springDataGroupMemberRepository.findByUserId(update.userId)

            userGroups.forEach { membership ->
                val groupId = membership.groupId

                // 3. Incrementa os pontos na tabela tbl_group_members no Postgres
                springDataGroupMemberRepository.incrementUserPoints(groupId, update.userId, update.pointsGained)

                // 4. Atualiza os Sorted Sets do Redis de cada grupo
                val groupOverallKey = "leaderboard:group:$groupId:overall"
                val groupStageKey = "leaderboard:group:$groupId:group-stage"
                val knockoutKey = "leaderboard:group:$groupId:knockout"

                leaderboardRepository.incrementScore(groupOverallKey, update.userId, update.pointsGained)
                leaderboardRepository.incrementScore(groupStageKey, update.userId, update.pointsGained)
                leaderboardRepository.incrementScore(knockoutKey, update.userId, update.pointsGained)
            }
        }
    }
}
