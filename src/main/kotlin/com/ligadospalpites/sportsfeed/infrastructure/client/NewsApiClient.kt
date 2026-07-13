package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class NewsApiClient(
    @Value("\${app.sportsfeed.news-api.url}") private val baseUrl: String,
    @Value("\${app.sportsfeed.news-api.api-key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(NewsApiClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .defaultHeader("X-Api-Key", apiKey)
        .build()

    fun fetchNews(query: String = "Copa do Mundo OR World Cup AND futebol", language: String = "pt"): List<NewsApiArticle> {
        logger.info("Fetching news from NewsAPI for query: '$query', language: $language")
        return try {
            val response = restClient.get()
                .uri { builder ->
                    builder.path("/v2/everything")
                        .queryParam("q", query)
                        .queryParam("language", language)
                        .queryParam("sortBy", "publishedAt")
                        .build()
                }
                .retrieve()
                .body(NewsApiResponse::class.java)
            response?.articles ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with NewsAPI: ${e.message}", e)
            throw e
        }
    }
}

data class NewsApiResponse(
    val status: String,
    val totalResults: Int = 0,
    val articles: List<NewsApiArticle> = emptyList()
)

data class NewsApiArticle(
    val title: String,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String? = null
)
