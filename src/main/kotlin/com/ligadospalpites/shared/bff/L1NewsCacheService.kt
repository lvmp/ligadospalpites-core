package com.ligadospalpites.shared.bff

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class L1NewsCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val environment: Environment
) {
    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    fun getCachedNews(cacheKey: String): List<NewsResponse> {
        return try {
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
    }
}
