package com.ligadospalpites.sportsfeed.application.usecases

import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

interface LeagueSyncService {
    fun supports(sportId: UUID, leagueId: UUID): Boolean
    fun syncMatches(sportId: UUID, leagueId: UUID)
    fun syncNews(sportId: UUID)
}

@Service
class SyncOrchestrator(
    private val syncServices: List<LeagueSyncService>,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val newsApiClient: com.ligadospalpites.sportsfeed.infrastructure.client.NewsApiClient,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(SyncOrchestrator::class.java)

    fun syncMatches(sportId: UUID, leagueId: UUID) {
        val service = syncServices.find { it.supports(sportId, leagueId) }
            ?: throw IllegalArgumentException("No sync service found for sport $sportId and league $leagueId")
        service.syncMatches(sportId, leagueId)
    }

    fun syncNews(sportId: UUID) {
        val service = syncServices.find { it.supports(sportId, UUID.randomUUID()) }
            ?: throw IllegalArgumentException("No sync service found for sport $sportId")
        service.syncNews(sportId)
    }

    fun syncAllActiveLeagues(force: Boolean): List<Map<String, Any>> {
        logger.info("Starting sync process for all active leagues. Force mode: $force")
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val now = Instant.now()
        val results = mutableListOf<Map<String, Any>>()

        for (league in activeLeagues) {
            val sportId = league.sportId
            val leagueId = league.id
            val leagueName = league.name

            val service = syncServices.find { it.supports(sportId, leagueId) }
            if (service == null) {
                logger.warn("No sync service found for active league $leagueName (ID: $leagueId, Sport: $sportId)")
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "SKIPPED",
                    "reason" to "No support service"
                ))
                continue
            }

            // Se NÃO for force, aplicamos o filtro inteligente de janela de atividade (exceto se a liga não tiver jogos cadastrados)
            if (!force) {
                val matches = matchRepository.findByLeagueId(leagueId)
                if (matches.isNotEmpty()) {
                    val hasActiveMatch = matches.any { match ->
                        val isLive = match.status == MatchStatus.LIVE
                        val isNearKickoff = match.kickoffTime?.let { kickoff ->
                            val startWindow = now.minus(15, ChronoUnit.MINUTES)
                            val endWindow = now.plus(3, ChronoUnit.HOURS)
                            kickoff.isAfter(startWindow) && kickoff.isBefore(endWindow)
                        } ?: false

                        isLive || isNearKickoff
                    }

                    if (!hasActiveMatch) {
                        logger.info("Skipping real-time sync for league $leagueName (ID: $leagueId) - No live or upcoming matches in the active window.")
                        results.add(mapOf(
                            "leagueId" to leagueId,
                            "leagueName" to leagueName,
                            "status" to "SKIPPED",
                            "reason" to "No active window matches"
                        ))
                        continue
                    }
                }
            }

            logger.info("Executing sync for league $leagueName (ID: $leagueId)")
            try {
                service.syncMatches(sportId, leagueId)
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "SUCCESS"
                ))
            } catch (e: Exception) {
                logger.error("Error syncing league $leagueName (ID: $leagueId): ${e.message}", e)
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "FAILED",
                    "error" to (e.message ?: "Unknown error")
                ))
            }
        }

        logger.info("Completed sync process for all active leagues. Results: $results")
        return results
    }

    fun syncAllActiveLeaguesNews(): List<Map<String, Any>> {
        logger.info("Starting automated sync process for news of all active leagues.")
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val results = mutableListOf<Map<String, Any>>()
        val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

        for (league in activeLeagues) {
            val sportId = league.sportId
            val leagueId = league.id
            val leagueName = league.name

            try {
                // Monta a query inteligente com base no esporte
                val query = if (sportId == UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")) {
                    "\"$leagueName\" AND (basquete OR basketball)"
                } else {
                    "\"$leagueName\" AND (futebol OR soccer OR football)"
                }

                logger.info("Sincronizando notícias da liga '$leagueName' ($leagueId) usando query: '$query'")
                val articles = newsApiClient.fetchNews(query = query, language = "pt")

                // 1. Tenta recuperar o histórico existente do cache
                val cacheKeyLeague = "news:$sportId:$leagueId"
                val cacheKeySport = "news:$sportId"
                val existingNewsJson = redisTemplate.opsForValue().get(cacheKeyLeague)
                    ?: redisTemplate.opsForValue().get(cacheKeySport)

                val existingNews: List<Map<String, String>> = if (!existingNewsJson.isNullOrBlank()) {
                    try {
                        objectMapper.readValue(
                            existingNewsJson,
                            objectMapper.typeFactory.constructCollectionType(List::class.java, Map::class.java)
                        )
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // 2. Mapeia os novos 10 artigos contendo "publishedAt"
                val newArticles = articles.take(10).map { art ->
                    mapOf(
                        "title" to art.title!!,
                        "url" to art.url!!,
                        "urlToImage" to (art.urlToImage ?: "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?q=80&w=600"),
                        "author" to (art.author ?: "Liga dos Palpites"),
                        "description" to (art.description ?: "Matéria completa disponível no link abaixo."),
                        "category" to leagueName,
                        "publishedAt" to (art.publishedAt ?: Instant.now().toString())
                    )
                }

                // 3. Mescla, desduplica e filtra por idade (máximo de 14 dias / 2 semanas)
                val fourteenDaysAgo = Instant.now().minus(14, java.time.temporal.ChronoUnit.DAYS)
                val combinedAndFiltered = (newArticles + existingNews)
                    .distinctBy { it["url"]?.trim()?.lowercase() ?: it["title"]?.trim()?.lowercase() }
                    .filter { art ->
                        val publishedAtStr = art["publishedAt"]
                        if (!publishedAtStr.isNullOrBlank()) {
                            try {
                                val publishedAt = Instant.parse(publishedAtStr)
                                publishedAt.isAfter(fourteenDaysAgo)
                            } catch (e: Exception) {
                                true
                            }
                        } else {
                            true
                        }
                    }
                    .sortedByDescending { art ->
                        val publishedAtStr = art["publishedAt"]
                        try {
                            if (!publishedAtStr.isNullOrBlank()) Instant.parse(publishedAtStr) else Instant.MIN
                        } catch (e: Exception) {
                            Instant.MIN
                        }
                    }

                val json = objectMapper.writeValueAsString(combinedAndFiltered)
                
                // Salva no Redis com as duas chaves (específica por liga e genérica por esporte para manter compatibilidade)
                redisTemplate.opsForValue().set(cacheKeyLeague, json)
                redisTemplate.opsForValue().set(cacheKeySport, json) // Fallback compatível

                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "SUCCESS",
                    "articlesSynced" to combinedAndFiltered.size
                ))
            } catch (e: Exception) {
                logger.error("Failed to sync news for league $leagueName ($leagueId): ${e.message}", e)
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "FAILED",
                    "error" to (e.message ?: "Unknown error")
                ))
            }
        }
        return results
    }
}

