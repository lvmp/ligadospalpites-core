package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.groups.domain.ports.GroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
