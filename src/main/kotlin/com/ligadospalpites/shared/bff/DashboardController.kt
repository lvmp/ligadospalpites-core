package com.ligadospalpites.shared.bff

import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.notifications.infrastructure.persistence.SpringDataInAppNotificationRepository
import com.ligadospalpites.shared.identity.UserResolver
import org.springframework.core.env.Environment
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/v1/home")
class DashboardController(
    private val groupMemberRepository: SpringDataGroupMemberRepository,
    private val groupRepository: SpringDataGroupRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val notificationRepository: SpringDataInAppNotificationRepository,
    private val leaderboardRepository: RedisLeaderboardRepository,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    private val userResolver: UserResolver,
    private val environment: Environment
) {

    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    private val executor: Executor = Executors.newFixedThreadPool(10)

    @GetMapping("/dashboard")
    fun getDashboard(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        @RequestParam(required = false) sportId: UUID? = null,
        @RequestParam(required = false) leagueId: UUID? = null
    ): CompletableFuture<ResponseEntity<DashboardResponse>> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        // 1. Fetch Rank and Points (Async)
        val rankAndPointsFuture = CompletableFuture.supplyAsync({
            val isGroup = leagueId != null && groupRepository.existsById(leagueId)
            val key = if (isGroup) "leaderboard:group:$leagueId:overall" else "leaderboard:global"
            val rankAndScore = leaderboardRepository.getUserRankAndScore(key, userUUID)
            val rank = rankAndScore.first?.toInt() ?: 1
            val score = rankAndScore.second?.toInt() ?: (if (isGroup) 0 else 120)
            Pair(rank, score)
        }, executor)

        // 2. Fetch Next Scheduled Matches (Async)
        val nextMatchesFuture = CompletableFuture.supplyAsync({
            var matches = matchRepository.findAll()
            matches = matches.filter { it.status.name == "SCHEDULED" }

            if (sportId != null) {
                matches = matches.filter { it.sportId == sportId }
            }

            if (leagueId != null) {
                val isGroup = groupRepository.existsById(leagueId)
                if (!isGroup) {
                    matches = matches.filter { it.leagueId == leagueId }
                }
            }

            matches.sortedBy { it.kickoffTime }
                .take(5)
                .map {
                    NextMatchResponse(
                        matchId = it.id,
                        homeTeam = it.homeTeamName,
                        awayTeam = it.awayTeamName,
                        kickoffTime = it.kickoffTime.toString(),
                        phase = it.phase
                    )
                }
        }, executor)

        // 3. Fetch User Groups Highlights (Async)
        val myGroupsFuture = CompletableFuture.supplyAsync({
            val userMemberships = groupMemberRepository.findAll().filter { it.userId == userUUID }
            userMemberships.mapNotNull { membership ->
                val group = groupRepository.findById(membership.groupId).orElse(null)
                if (group != null) {
                    val key = "leaderboard:group:${group.id}:overall"
                    val (rank, _) = leaderboardRepository.getUserRankAndScore(key, userUUID)
                    val totalMembers = groupMemberRepository.findAll().count { it.groupId == group.id }

                    GroupHighlightResponse(
                        groupId = group.id,
                        groupName = group.name,
                        userRank = rank?.toInt() ?: 1,
                        totalMembers = totalMembers,
                        logoUrl = group.logoUrl ?: "https://api.dicebear.com/7.x/initials/svg?seed=${group.name}"
                    )
                } else null
            }
        }, executor)

        // 4. Fetch News (Async from Redis Cache with Fallback)
        val newsFuture = CompletableFuture.supplyAsync({
            val targetSportId = sportId ?: UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
            val isGroup = leagueId != null && groupRepository.existsById(leagueId)
            val cacheKey = if (leagueId != null && !isGroup) "news:$targetSportId:$leagueId" else "news:$targetSportId"
            try {
                val cachedNewsJson = redisTemplate.opsForValue().get(cacheKey)
                if (!cachedNewsJson.isNullOrBlank()) {
                    val articles: List<Map<String, String>> = objectMapper.readValue(
                        cachedNewsJson,
                        objectMapper.typeFactory.constructCollectionType(List::class.java, Map::class.java)
                    )
                    articles.map { art ->
                        NewsResponse(
                            title = art["title"] ?: "",
                            url = art["url"] ?: "",
                            urlToImage = art["urlToImage"] ?: "",
                            author = art["author"] ?: "Liga dos Palpites",
                            description = art["description"] ?: "Matéria completa disponível no link abaixo.",
                            category = art["category"] ?: "Copa do Mundo"
                        )
                    }
                } else {
                    // Fallback se o Redis estiver limpo ou recém-criado
                    if (environment.activeProfiles.contains("prod")) {
                        emptyList()
                    } else {
                        listOf(
                            NewsResponse(
                                title = "Brasil se prepara para enfrentar a França na final da Copa",
                                url = "https://ge.globo.com/copa/news1.html",
                                urlToImage = "https://ge.globo.com/image1.png",
                                author = "Liga dos Palpites",
                                description = "Matéria completa disponível no link abaixo.",
                                category = "Copa do Mundo"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Em caso de falha de conexão do Redis, devolvemos o fallback amigável sem quebrar o BFF!
                if (environment.activeProfiles.contains("prod")) {
                    emptyList()
                } else {
                    listOf(
                        NewsResponse(
                            title = "Brasil se prepara para enfrentar a França na final da Copa",
                            url = "https://ge.globo.com/copa/news1.html",
                            urlToImage = "https://ge.globo.com/image1.png",
                            author = "Liga dos Palpites",
                            description = "Matéria completa disponível no link abaixo.",
                            category = "Copa do Mundo"
                        )
                    )
                }
            }
        }, executor)

        // 5. Check Unread Notifications (Async)
        val hasNotificationsFuture = CompletableFuture.supplyAsync({
            notificationRepository.existsByUserIdAndIsReadFalse(userUUID)
        }, executor)

        // Aggregate All
        return CompletableFuture.allOf(
            rankAndPointsFuture,
            nextMatchesFuture,
            myGroupsFuture,
            newsFuture,
            hasNotificationsFuture
        ).thenApply {
            val (rank, points) = rankAndPointsFuture.join()
            val nextMatches = nextMatchesFuture.join()
            val myGroups = myGroupsFuture.join()
            val news = newsFuture.join()
            val hasUnread = hasNotificationsFuture.join()

            ResponseEntity.ok(
                DashboardResponse(
                    userId = userUUID,
                    points = points,
                    rankGlobal = rank,
                    nextMatches = nextMatches,
                    myGroupsHighlight = myGroups,
                    news = news,
                    hasUnreadNotifications = hasUnread
                )
            )
        }
    }
}

// DTOs
data class DashboardResponse(
    val userId: UUID,
    val points: Int,
    val rankGlobal: Int,
    val nextMatches: List<NextMatchResponse>,
    val myGroupsHighlight: List<GroupHighlightResponse>,
    val news: List<NewsResponse>,
    val hasUnreadNotifications: Boolean
)

data class NextMatchResponse(
    val matchId: UUID,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffTime: String,
    val phase: String? = null
)

data class GroupHighlightResponse(
    val groupId: UUID,
    val groupName: String,
    val userRank: Int,
    val totalMembers: Int,
    val logoUrl: String? = null
)

data class NewsResponse(
    val title: String,
    val url: String,
    val urlToImage: String,
    val author: String,
    val description: String,
    val category: String
)
