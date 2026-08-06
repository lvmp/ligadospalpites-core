package com.ligadospalpites.notifications.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
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
                val firebaseCredentialsEnv = System.getenv("FIREBASE_CREDENTIALS") 
                    ?: System.getenv("FIREBASE_CREDENTIALS_JSON")

                val credentials = if (!firebaseCredentialsEnv.isNullOrBlank()) {
                    tryLoadCustomCredentials(firebaseCredentialsEnv)
                        ?: loadApplicationDefaultCredentials()
                } else {
                    loadApplicationDefaultCredentials()
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

    private fun loadApplicationDefaultCredentials(): GoogleCredentials {
        log.info("Carregando credenciais padrão do Google Cloud Application Default Credentials (ADC).")
        return GoogleCredentials.getApplicationDefault()
    }

    private fun tryLoadCustomCredentials(rawInput: String): GoogleCredentials? {
        return try {
            val jsonBytes = extractJsonBytes(rawInput) ?: return null
            GoogleCredentials.fromStream(ByteArrayInputStream(jsonBytes))
        } catch (ex: Exception) {
            log.warn("Falha ao analisar a variável de ambiente de credenciais do Firebase: {}. Retornando para Application Default Credentials.", ex.message)
            null
        }
    }

    private fun extractJsonBytes(rawInput: String): ByteArray? {
        var input = rawInput.trim().removePrefix("\uFEFF")

        if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {
            input = input.substring(1, input.length - 1).trim()
        }

        if (input.contains("\\n") || input.contains("\\\"")) {
            input = input.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        input = input.trim()

        if (input.startsWith("{")) {
            log.info("Carregando credenciais do Firebase a partir de texto JSON bruto.")
            return input.toByteArray(Charsets.UTF_8)
        }

        log.info("Detectada credencial criptografada/Base64. Decodificando...")
        val cleanedBase64 = input.replace(Regex("\\s+"), "")

        val decodedBytes = try {
            java.util.Base64.getDecoder().decode(cleanedBase64)
        } catch (e: Exception) {
            try {
                java.util.Base64.getMimeDecoder().decode(cleanedBase64)
            } catch (e2: Exception) {
                log.warn("A string de credenciais não inicia com '{' e falhou ao decodificar Base64: {}", e.message)
                return null
            }
        }

        val decodedString = String(decodedBytes, Charsets.UTF_8).trim().removePrefix("\uFEFF")
        if (decodedString.startsWith("{")) {
            log.info("Credenciais decodificadas do Base64 com sucesso.")
            return decodedBytes
        }

        log.warn("Conteúdo decodificado de Base64 não é um JSON válido (não inicia com '{').")
        return null
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

    @Bean
    fun firebaseAuth(): FirebaseAuth? {
        return try {
            initializeFirebase()
            FirebaseAuth.getInstance()
        } catch (ex: Exception) {
            log.warn("O serviço Firebase Auth não pôde ser inicializado: {}. Consultas ao Firebase Auth serão simuladas.", ex.message)
            null
        }
    }
}
