package com.ligadospalpites.users.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class RiotGamesClient(
    @Value("\${app.riot.api-key:}") private val apiKey: String,
    @Value("\${app.riot.account-url:https://americas.api.riotgames.com}") private val accountUrl: String,
    @Value("\${app.riot.lol-url:https://br1.api.riotgames.com}") private val lolUrl: String
) {
    private val logger = LoggerFactory.getLogger(RiotGamesClient::class.java)

    private val accountClient: RestClient by lazy {
        RestClient.builder()
            .baseUrl(accountUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5000)
                setReadTimeout(10000)
            })
            .defaultHeader("X-Riot-Token", apiKey)
            .build()
    }

    private val lolClient: RestClient by lazy {
        RestClient.builder()
            .baseUrl(lolUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5000)
                setReadTimeout(10000)
            })
            .defaultHeader("X-Riot-Token", apiKey)
            .build()
    }

    fun getAccountByRiotId(gameName: String, tagLine: String): RiotAccountResponse? {
        if (apiKey.isBlank()) {
            logger.warn("Riot API key is empty. Generating synthetic PUUID for $gameName#$tagLine.")
            val syntheticPuuid = "puuid-synth-${(gameName + tagLine).lowercase().hashCode()}"
            return RiotAccountResponse(puuid = syntheticPuuid, gameName = gameName, tagLine = tagLine)
        }

        return try {
            logger.info("Resolving Riot ID account for $gameName#$tagLine")
            accountClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(RiotAccountResponse::class.java)
        } catch (e: Exception) {
            logger.error("Failed to query Riot Account API: ${e.message}")
            val syntheticPuuid = "puuid-synth-${(gameName + tagLine).lowercase().hashCode()}"
            RiotAccountResponse(puuid = syntheticPuuid, gameName = gameName, tagLine = tagLine)
        }
    }

    fun getLolRankByPuuid(puuid: String): String {
        if (apiKey.isBlank()) {
            return "Ouro IV"
        }

        return try {
            val summoner = lolClient.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotSummonerResponse::class.java) ?: return "Unranked"

            val entries = lolClient.get()
                .uri("/lol/league/v4/entries/by-summoner/{summonerId}", summoner.id)
                .retrieve()
                .body(Array<RiotLeagueEntry>::class.java) ?: emptyArray()

            val soloEntry = entries.find { it.queueType == "RANKED_SOLO_5x5" } ?: entries.firstOrNull()
            if (soloEntry != null) {
                "${formatRankTier(soloEntry.tier)} ${soloEntry.rank}"
            } else {
                "Unranked"
            }
        } catch (e: Exception) {
            logger.error("Failed to query Riot LoL Rank API: ${e.message}")
            "Ouro IV"
        }
    }

    private fun formatRankTier(tier: String): String {
        return when (tier.uppercase()) {
            "IRON" -> "Ferro"
            "BRONZE" -> "Bronze"
            "SILVER" -> "Prata"
            "GOLD" -> "Ouro"
            "PLATINUM" -> "Platina"
            "EMERALD" -> "Esmeralda"
            "DIAMOND" -> "Diamante"
            "MASTER" -> "Mestre"
            "GRANDMASTER" -> "Grão-Mestre"
            "CHALLENGER" -> "Desafiante"
            else -> tier
        }
    }
}

data class RiotAccountResponse(
    val puuid: String,
    val gameName: String,
    val tagLine: String
)

data class RiotSummonerResponse(
    val id: String,
    val accountId: String,
    val puuid: String
)

data class RiotLeagueEntry(
    val queueType: String,
    val tier: String,
    val rank: String,
    val leaguePoints: Int
)
