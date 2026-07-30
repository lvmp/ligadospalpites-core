package com.ligadospalpites.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val bearerAuthSchemeName = "bearerAuth"
        val userIdHeaderSchemeName = "X-User-Id"

        return OpenAPI()
            .info(
                Info()
                    .title("Ligados Palpites API")
                    .version("1.0")
                    .description("Documentação da API do Ligados Palpites com suporte a Autenticação por Token (JWT / Firebase)")
                    .contact(Contact().name("Ligados Palpites Team"))
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        bearerAuthSchemeName,
                        SecurityScheme()
                            .name(bearerAuthSchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Token Bearer JWT de Autenticação (Firebase Auth)")
                    )
                    .addSecuritySchemes(
                        userIdHeaderSchemeName,
                        SecurityScheme()
                            .name(userIdHeaderSchemeName)
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .description("ID de usuário para testes ou simulação (UUID ou Firebase UID)")
                    )
            )
    }

    @Bean
    fun objectMapper(): com.fasterxml.jackson.databind.ObjectMapper {
        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    }
}
