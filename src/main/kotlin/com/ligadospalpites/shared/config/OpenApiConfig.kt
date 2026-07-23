package com.ligadospalpites.shared.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Ligados Palpites API")
                    .version("1.0")
                    .description("Documentação da API do Ligados Palpites")
                    .contact(Contact().name("Ligados Palpites Team"))
            )
    }

    @Bean
    fun objectMapper(): com.fasterxml.jackson.databind.ObjectMapper {
        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    }
}
