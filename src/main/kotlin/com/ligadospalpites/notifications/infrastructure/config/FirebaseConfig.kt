package com.ligadospalpites.notifications.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream

@Configuration
class FirebaseConfig {

    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    private fun initializeFirebase() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                // Support both custom FIREBASE_CREDENTIALS env variable (JSON string or Base64 encoded JSON) and App Default Credentials
                val firebaseCredentialsJson = System.getenv("FIREBASE_CREDENTIALS") 
                    ?: System.getenv("FIREBASE_CREDENTIALS_JSON")

                val credentials = if (!firebaseCredentialsJson.isNullOrBlank()) {
                    val trimmed = firebaseCredentialsJson.trim()
                    
                    // Remove all white spaces and line breaks that command line encoders generate (e.g. wrapping every 76 chars)
                    val cleaned = trimmed.replace(Regex("\\s+"), "")

                    val jsonBytes = if (!trimmed.startsWith("{")) {
                        log.info("Detectada credencial criptografada/Base64. Decodificando...")
                        try {
                            java.util.Base64.getDecoder().decode(cleaned)
                        } catch (e: Exception) {
                            log.warn("A string não inicia com '{' mas falhou ao decodificar Base64. Usando dados originais: {}", e.message)
                            firebaseCredentialsJson.toByteArray()
                        }
                    } else {
                        log.info("Carregando credenciais do Firebase a partir de texto JSON bruto.")
                        firebaseCredentialsJson.toByteArray()
                    }
                    GoogleCredentials.fromStream(ByteArrayInputStream(jsonBytes))
                } else {
                    log.info("Carregando credenciais padrão do Google Cloud Application Default.")
                    GoogleCredentials.getApplicationDefault()
                }

                val projectId = System.getenv("FIREBASE_PROJECT_ID")
                    ?: System.getenv("GOOGLE_CLOUD_PROJECT")
                    ?: System.getenv("GCP_PROJECT")

                val optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(credentials)

                if (!projectId.isNullOrBlank()) {
                    log.info("Configurando ID do projeto Firebase: {}", projectId)
                    optionsBuilder.setProjectId(projectId)
                    optionsBuilder.setDatabaseUrl("https://$projectId.firebaseio.com")
                }

                val options = optionsBuilder.build()
                FirebaseApp.initializeApp(options)
                log.info("Firebase inicializado com sucesso.")
            } catch (ex: Exception) {
                log.error("Erro crítico ao tentar inicializar o FirebaseApp: ${ex.message}", ex)
                throw ex
            }
        }
    }

    @Bean
    fun firebaseMessaging(): FirebaseMessaging? {
        return try {
            initializeFirebase()
            FirebaseMessaging.getInstance()
        } catch (ex: Exception) {
            log.warn("O serviço de mensagens Firebase (FCM) não pôde ser inicializado: {}. Pushes serão simulados.", ex.message)
            null
        }
    }

    @Bean
    fun firestore(): Firestore? {
        return try {
            initializeFirebase()
            FirestoreClient.getFirestore()
        } catch (ex: Exception) {
            log.error("O serviço de banco de dados Firestore não pôde ser inicializado: {}. Consultas reais do Firestore serão simuladas.", ex.message, ex)
            null
        }
    }
}
