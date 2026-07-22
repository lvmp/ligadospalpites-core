package com.ligadospalpites.shared.config

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class FlywayConfiguration {

    private val log = LoggerFactory.getLogger(FlywayConfiguration::class.java)

    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource): Flyway {
        log.info("Inicializando e executando migrações do Flyway de forma programática...")
        return Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(true)
            .outOfOrder(true)
            .locations("classpath:db/migration")
            .load()
    }
}
