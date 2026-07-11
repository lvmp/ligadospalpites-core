package com.ligadospalpites.notifications.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream

@Configuration
class FirebaseConfig {

    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Bean
    fun firebaseMessaging(): FirebaseMessaging? {
        return try {
            if (FirebaseApp.getApps().isEmpty()) {
                // Try initializing with environment variables or default credentials
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build()
                FirebaseApp.initializeApp(options)
                log.info("Firebase initialized successfully using default credentials.")
            }
            FirebaseMessaging.getInstance()
        } catch (ex: Exception) {
            log.warn("FCM Firebase credentials are not configured or failed to initialize: {}. FCM pushes will be simulated/logged.", ex.message)
            null
        }
    }
}
