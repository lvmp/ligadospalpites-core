package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class DispatchDailyAgendaPushUseCase(
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val dispatcherService: NotificationDispatcherService,
    private val redisTemplate: StringRedisTemplate? = null
) {
    private val logger = LoggerFactory.getLogger(DispatchDailyAgendaPushUseCase::class.java)

    fun execute() {
        logger.info("Executing DispatchDailyAgendaPushUseCase for today's matches.")

        val zoneId = ZoneId.of("America/Sao_Paulo")
        val today = LocalDate.now(zoneId)
        val redisKey = "daily_agenda_push:sent:$today"

        if (redisTemplate != null) {
            val alreadySent = redisTemplate.opsForValue().get(redisKey)
            if (alreadySent != null) {
                logger.info("Daily agenda push already dispatched today ($today). Skipping duplicate push.")
                return
            }
        }

        val activeLeagues = leagueRepository.findByIsActiveTrue()
        if (activeLeagues.isEmpty()) {
            logger.info("No active leagues found. Skipping daily agenda push.")
            return
        }

        val startOfToday = today.atStartOfDay(zoneId).toInstant()
        val endOfToday = today.atTime(23, 59, 59, 999_999_999).atZone(zoneId).toInstant()

        val activeLeagueIds = activeLeagues.map { it.id }
        val statuses = listOf(MatchStatus.SCHEDULED, MatchStatus.LIVE, MatchStatus.HALF_TIME)

        val matchesToday = matchRepository.findUpcomingMatchesByLeagueIds(
            statuses = statuses,
            startTime = startOfToday,
            endTime = endOfToday,
            leagueIds = activeLeagueIds,
            sportId = null
        )

        if (matchesToday.isEmpty()) {
            logger.info("No matches scheduled for today. Skipping daily agenda notification dispatch.")
            return
        }

        val title = "📅 AGENDA DO DIA - Liga dos Palpites"
        val matchesByLeague = matchesToday.groupBy { it.leagueId }
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

        val contentLines = mutableListOf<String>()
        contentLines.add("Confira os jogos de hoje:")
        contentLines.add("")

        activeLeagues.forEach { league ->
            val matches = matchesByLeague[league.id]
            if (!matches.isNullOrEmpty()) {
                contentLines.add("🏆 ${league.name}:")
                matches.forEach { m ->
                    val formattedTime = timeFormatter.format(m.kickoffTime)
                    contentLines.add("• ${m.homeTeamName} x ${m.awayTeamName} ($formattedTime)")
                }
                contentLines.add("")
            }
        }

        val content = contentLines.joinToString("\n").trim()

        logger.info("Dispatching daily agenda notification to ALL users.")
        dispatcherService.dispatch(
            target = NotificationTarget.ALL,
            targetId = null,
            title = title,
            content = content,
            channels = listOf(NotificationChannel.PUSH, NotificationChannel.IN_APP)
        )

        try {
            redisTemplate?.opsForValue()?.set(redisKey, "SENT", Duration.ofHours(24))
        } catch (e: Exception) {
            logger.warn("Failed to set redis key $redisKey: ${e.message}")
        }
    }
}
