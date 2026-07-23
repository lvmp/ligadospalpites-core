package com.ligadospalpites.users.application.usecases

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.ligadospalpites.groups.infrastructure.persistence.GroupJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataSpecialPredictionRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class MigrationSummaryDto(
    val status: String,
    val simulated: Boolean, // Jackson converterá para 'simulated' na serialização
    val usersProcessed: Int,
    val usersCreated: Int,
    val groupsMigrated: Int,
    val groupMembersMigrated: Int,
    val predictionsMigrated: Int,
    val specialPredictionsMigrated: Int = 0,
    val executionTimeMs: Long,
    val warnings: List<String>
)

@Service
class FirebaseUserMigrationUseCase(
    private val userRepository: SpringDataUserRepository,
    private val groupRepository: SpringDataGroupRepository,
    private val groupMemberRepository: SpringDataGroupMemberRepository,
    private val predictionRepository: SpringDataPredictionRepository,
    private val specialPredictionRepository: SpringDataSpecialPredictionRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val redisTemplate: StringRedisTemplate,
    private val firestore: Firestore?
) {
    private val log = LoggerFactory.getLogger(FirebaseUserMigrationUseCase::class.java)

    @Transactional
    fun execute(forceSimulation: Boolean = false): MigrationSummaryDto {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        val runSimulation = forceSimulation || firestore == null
        if (runSimulation) {
            log.info("Iniciando migração de usuários e palpites em MODO SIMULAÇÃO.")
            return executeSimulation(startTime)
        }

        log.info("Iniciando migração de usuários e palpites em MODO REAL (Firebase Firestore conectado).")
        val fs = firestore
        try {
            val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
            val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
            val worldCupSeasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

            // FASE 0: Migrar Partidas do Firestore para o PostgreSQL (tbl_matches) se estiver vazio ou para atualizar
            log.info("FASE 0: Verificando/Migrando partidas do Firestore para o PostgreSQL...")
            var matchesCollection = fs.collection("matches")
            var documents = matchesCollection.get().get().documents
            if (documents.isEmpty()) {
                matchesCollection = fs.collection("fixtures")
                documents = matchesCollection.get().get().documents
            }
            if (documents.isEmpty()) {
                matchesCollection = fs.collection("partidas")
                documents = matchesCollection.get().get().documents
            }

            if (documents.isNotEmpty()) {
                log.info("Encontradas {} partidas no Firestore. Sincronizando com tbl_matches...", documents.size)
                for (doc in documents) {
                    val docId = doc.id // Ex: "match_102"
                    // Gera um UUID determinístico a partir do ID do documento do Firestore
                    val matchUuid = UUID.nameUUIDFromBytes(docId.toByteArray())
                    
                    val home = doc.getString("homeTeam") ?: doc.getString("homeTeamName") ?: doc.getString("home_team") ?: doc.getString("home") ?: "Time A"
                    val away = doc.getString("awayTeam") ?: doc.getString("awayTeamName") ?: doc.getString("away_team") ?: doc.getString("away") ?: "Time B"
                    
                    val kickoffVal = doc.get("kickoffTime") ?: doc.get("date") ?: doc.get("kickoff")
                    val kickoffTime = try {
                        parseTimestamp(kickoffVal)
                    } catch (e: Exception) {
                        Instant.now()
                    }

                    val statusStr = doc.getString("status") ?: "SCHEDULED"
                    val status = when (statusStr.uppercase()) {
                        "FINISHED", "CONCLUIDO" -> com.ligadospalpites.sportsfeed.domain.models.MatchStatus.FINISHED
                        "LIVE", "AO_VIVO", "IN_PLAY" -> com.ligadospalpites.sportsfeed.domain.models.MatchStatus.LIVE
                        "CANCELLED" -> com.ligadospalpites.sportsfeed.domain.models.MatchStatus.CANCELLED
                        else -> com.ligadospalpites.sportsfeed.domain.models.MatchStatus.SCHEDULED
                    }

                    val homeScore = doc.getLong("homeScore")?.toInt() ?: doc.getLong("home_score")?.toInt()
                    val awayScore = doc.getLong("awayScore")?.toInt() ?: doc.getLong("away_score")?.toInt()
                    val phase = doc.getString("phase") ?: doc.getString("stage") ?: "Fase de Grupos"

                    if (!matchRepository.existsById(matchUuid)) {
                        val newMatch = MatchJpaEntity(
                            id = matchUuid,
                            sportId = footballId,
                            leagueId = worldCupLeagueId,
                            seasonId = worldCupSeasonId,
                            homeTeamName = home,
                            awayTeamName = away,
                            kickoffTime = kickoffTime,
                            status = status,
                            homeScore = homeScore,
                            awayScore = awayScore,
                            phase = phase,
                            updatedAt = Instant.now()
                        )
                        matchRepository.save(newMatch)
                    }
                }
                log.info("Sincronização de partidas da FASE 0 concluída.")
            } else {
                log.warn("Nenhuma partida encontrada nas coleções do Firestore ('matches', 'fixtures', 'partidas').")
            }

            // 1. Carregar partidas e preparar mapa de confrontos do Firestore
            val sortedMatches = matchRepository.findAll()
            if (sortedMatches.isEmpty()) {
                val warnMsg = "Nenhuma partida encontrada no PostgreSQL local. Por favor, rode a sincronização de partidas antes de migrar os palpites."
                log.warn(warnMsg)
                warnings.add(warnMsg)
            }

            val matchLookup = buildMatchLookup(warnings)

            // 2. FASE 1: Migrar Usuários ('users')
            val usersCollection = fs.collection("users")
            val usersDocs = usersCollection.get().get().documents
            var usersProcessedCount = 0
            var usersCreatedCount = 0

            log.info("Buscados {} usuários da coleção 'users' no Firestore.", usersDocs.size)
            for (userDoc in usersDocs) {
                val firebaseUid = userDoc.id
                val email = userDoc.getString("email") ?: "${firebaseUid}@migrated.com"
                val name = userDoc.getString("name") ?: userDoc.getString("displayName") ?: "Usuário Migrado"
                val createdAt = parseTimestamp(userDoc.get("createdAt"))

                val existing = userRepository.findByFirebaseUid(firebaseUid)
                if (existing == null) {
                    val newUser = UserJpaEntity(
                        id = UUID.randomUUID(),
                        firebaseUid = firebaseUid,
                        email = email,
                        name = name,
                        createdAt = createdAt
                    )
                    userRepository.save(newUser)
                    usersCreatedCount++
                }
                usersProcessedCount++
            }

            // 3. FASE 2: Migrar Ligas ('groups')
            val groupsCollection = fs.collection("groups")
            val groupsDocs = groupsCollection.get().get().documents
            var groupsMigratedCount = 0

            log.info("Buscados {} grupos da coleção 'groups' no Firestore.", groupsDocs.size)
            for (groupDoc in groupsDocs) {
                val firestoreGroupId = groupDoc.id
                // ID determinístico para consistência idempotente e preservação de referências
                val postgresGroupId = UUID.nameUUIDFromBytes(firestoreGroupId.toByteArray())
                val nameLiga = groupDoc.getString("nameLiga") ?: groupDoc.getString("name") ?: "Liga $firestoreGroupId"
                val createdBy = groupDoc.getString("createdBy") ?: ""
                val createdAt = parseTimestamp(groupDoc.get("createdAt"))

                // Resolve o criador no banco Postgres
                val creator = userRepository.findByFirebaseUid(createdBy)
                val creatorId = creator?.id ?: run {
                    // Fallback para o primeiro usuário cadastrado ou ID aleatório consistente
                    val defaultUser = userRepository.findAll().firstOrNull()
                    defaultUser?.id ?: UUID.randomUUID()
                }

                if (!groupRepository.existsById(postgresGroupId)) {
                    val newGroup = GroupJpaEntity(
                        id = postgresGroupId,
                        name = nameLiga,
                        creatorId = creatorId,
                        scoringRulesJson = "{\"pointsExactScore\":10,\"pointsCorrectWinner\":5,\"pointsGoalDifference\":3}",
                        createdAt = createdAt
                    )
                    groupRepository.save(newGroup)
                }
                groupsMigratedCount++
            }

            // 4. FASE 3: Migrar Membros (Estratégia de busca resiliente e exaustiva em múltiplas coleções possíveis)
            val possibleMembersCollections = listOf(
                "groupmembers",
                "groupMembers",
                "groupMember",
                "groupmember",
                "group_members",
                "group_member"
            )
            
            var membersDocs = emptyList<com.google.cloud.firestore.QueryDocumentSnapshot>()
            var resolvedCollectionName = "groupmembers"
            
            for (colName in possibleMembersCollections) {
                val docs = fs.collection(colName).get().get().documents
                if (docs.isNotEmpty()) {
                    membersDocs = docs
                    resolvedCollectionName = colName
                    break
                }
            }

            var groupMembersMigratedCount = 0
            log.info("Buscados {} membros da coleção de associados '{}' no Firestore.", membersDocs.size, resolvedCollectionName)
            for (memberDoc in membersDocs) {
                val firestoreGroupId = memberDoc.getString("groupId") ?: ""
                val firebaseUid = memberDoc.getString("userId") ?: ""
                val totalScore = memberDoc.getLong("totalScore")?.toInt() ?: 0
                val groupStageScore = memberDoc.getLong("groupStageScore")?.toInt() ?: 0
                val knockoutScore = memberDoc.getLong("knockoutScore")?.toInt() ?: 0
                val joinedAt = parseTimestamp(memberDoc.get("joinedAt"))

                val postgresGroupId = UUID.nameUUIDFromBytes(firestoreGroupId.toByteArray())
                val localUser = userRepository.findByFirebaseUid(firebaseUid)

                if (localUser != null && groupRepository.existsById(postgresGroupId)) {
                    val localUserId = localUser.id
                    val memberEntity = GroupMemberJpaEntity(
                        groupId = postgresGroupId,
                        userId = localUserId,
                        joinedAt = joinedAt,
                        accumulatedPoints = totalScore
                    )
                    groupMemberRepository.save(memberEntity)

                    // Inicializar no Redis usando scores históricos migrados
                    val overallKey = "leaderboard:group:$postgresGroupId:overall"
                    val groupStageKey = "leaderboard:group:$postgresGroupId:group-stage"
                    val knockoutKey = "leaderboard:group:$postgresGroupId:knockout"

                    redisTemplate.opsForZSet().add(overallKey, localUserId.toString(), totalScore.toDouble())
                    redisTemplate.opsForZSet().add(groupStageKey, localUserId.toString(), groupStageScore.toDouble())
                    redisTemplate.opsForZSet().add(knockoutKey, localUserId.toString(), knockoutScore.toDouble())

                    // Definir no Ranking Global (usando add para ser idempotente e não somar em duplicidade por grupo)
                    val globalKey = "leaderboard:global"
                    redisTemplate.opsForZSet().add(globalKey, localUserId.toString(), totalScore.toDouble())

                    groupMembersMigratedCount++
                } else {
                    warnings.add("Não foi possível migrar associação groupMember '${memberDoc.id}'. Grupo ou Usuário correspondente não encontrado localmente.")
                }
            }

            // 5. FASE 4: Migrar Palpites ('prediction') para cada usuário
            var predictionsMigratedCount = 0
            for (userDoc in usersDocs) {
                val firebaseUid = userDoc.id
                val localUser = userRepository.findByFirebaseUid(firebaseUid) ?: continue
                val localUserId = localUser.id

                // Tenta consultar coleção "prediction" e se vazia tenta "predictions"
                var predictionsCollection = fs.collection("users").document(firebaseUid).collection("prediction")
                var predictionDocs = predictionsCollection.get().get().documents
                if (predictionDocs.isEmpty()) {
                    predictionsCollection = fs.collection("users").document(firebaseUid).collection("predictions")
                    predictionDocs = predictionsCollection.get().get().documents
                }

                for (predDoc in predictionDocs) {
                    val docData = predDoc.data
                    // Procurar chaves que combinem com match_<numero> ou mapear diretamente da raiz do documento
                    val predictionMaps = docData.filter { it.key.startsWith("match_") && it.value is Map<*, *> }

                    if (predictionMaps.isNotEmpty()) {
                        for ((_, matchMapRaw) in predictionMaps) {
                            val matchMap = matchMapRaw as Map<*, *>
                            val success = processPredictionMap(
                                matchMap,
                                localUserId,
                                sortedMatches,
                                matchLookup,
                                warnings
                            )
                            if (success) predictionsMigratedCount++
                        }
                    } else {
                        // Trata como predição direta na raiz do documento (flat)
                        val success = processPredictionMap(
                            docData,
                            localUserId,
                            sortedMatches,
                            matchLookup,
                            warnings
                        )
                        if (success) predictionsMigratedCount++
                    }
                }
            }

            // 6. FASE 5: Migrar Palpites Especiais ('specialPredictions')
            val possibleSpecialCollections = listOf(
                "specialPredictions",
                "special_predictions",
                "specialPrediction",
                "special_prediction"
            )
            
            var specialDocs = emptyList<com.google.cloud.firestore.QueryDocumentSnapshot>()
            for (colName in possibleSpecialCollections) {
                val docs = fs.collection(colName).get().get().documents
                if (docs.isNotEmpty()) {
                    specialDocs = docs
                    break
                }
            }

            var specialPredictionsMigratedCount = 0
            if (specialDocs.isNotEmpty()) {
                log.info("FASE 5: Sincronizando {} palpites especiais com tbl_special_predictions...", specialDocs.size)
                for (specialDoc in specialDocs) {
                    val docData = specialDoc.data
                    val firebaseUid = docData["userId"]?.toString() ?: ""
                    val localUser = userRepository.findByFirebaseUid(firebaseUid)
                    if (localUser != null) {
                        val localUserId = localUser.id
                        val createdAtVal = docData["createdAt"] ?: docData["updatedAt"]
                        val createdAt = try {
                            parseTimestamp(createdAtVal)
                        } catch (e: Exception) {
                            Instant.now()
                        }

                        // Mapeia os quatro primeiros colocados para as chaves correspondentes
                        val places = mapOf(
                            "CHAMPION" to (docData["teamId"]?.toString() ?: docData["championTeamId"]?.toString()),
                            "SECOND_PLACE" to docData["secondPlaceTeamId"]?.toString(),
                            "THIRD_PLACE" to docData["thirdPlaceTeamId"]?.toString(),
                            "FOURTH_PLACE" to docData["fourthPlaceTeamId"]?.toString()
                        )

                        for ((type, teamId) in places) {
                            if (!teamId.isNullOrBlank()) {
                                val exists = specialPredictionRepository.findByUserIdAndLeagueIdAndType(
                                    localUserId,
                                    worldCupLeagueId,
                                    type
                                ) != null
                                
                                if (!exists) {
                                    val entity = SpecialPredictionJpaEntity(
                                        id = UUID.randomUUID(),
                                        userId = localUserId,
                                        leagueId = worldCupLeagueId,
                                        type = type,
                                        predictionValue = teamId,
                                        pointsAwarded = 0,
                                        isProcessed = false,
                                        createdAt = createdAt
                                    )
                                    specialPredictionRepository.save(entity)
                                    specialPredictionsMigratedCount++
                                }
                            }
                        }
                    }
                }
            }

            val executionTime = System.currentTimeMillis() - startTime
            return MigrationSummaryDto(
                status = "COMPLETED",
                simulated = false,
                usersProcessed = usersProcessedCount,
                usersCreated = usersCreatedCount,
                groupsMigrated = groupsMigratedCount,
                groupMembersMigrated = groupMembersMigratedCount,
                predictionsMigrated = predictionsMigratedCount,
                specialPredictionsMigrated = specialPredictionsMigratedCount,
                executionTimeMs = executionTime,
                warnings = warnings
            )

        } catch (ex: Exception) {
            log.error("Erro fatal durante execução da migração real do Firebase: {}", ex.message, ex)
            return MigrationSummaryDto(
                status = "FAILED",
                simulated = false,
                usersProcessed = 0,
                usersCreated = 0,
                groupsMigrated = 0,
                groupMembersMigrated = 0,
                predictionsMigrated = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                warnings = listOf("Erro: ${ex.message}")
            )
        }
    }

    private fun processPredictionMap(
        map: Map<*, *>,
        localUserId: UUID,
        sortedMatches: List<MatchJpaEntity>,
        matchLookup: Map<String, Pair<String, String>>,
        warnings: MutableList<String>
    ): Boolean {
        val matchIdStr = map["matchId"]?.toString() ?: return false
        val scoreA = (map["scoreA"] as? Number)?.toInt() ?: 0
        val scoreB = (map["scoreB"] as? Number)?.toInt() ?: 0
        val pointsAwarded = (map["pointsAwarded"] as? Number)?.toInt() ?: 0
        val isProcessed = map["isProcessed"] as? Boolean ?: (pointsAwarded > 0)
        val updatedAt = parseTimestamp(map["updatedAt"] ?: map["createdAt"])

        // Resolve o Match UUID a partir da string de confronto ou UUID direto
        val resolvedMatch = resolveMatch(matchIdStr, sortedMatches, matchLookup)
        if (resolvedMatch == null) {
            warnings.add("Palpite ignorado: Match de ID '$matchIdStr' não pôde ser mapeado para nenhuma partida local.")
            return false
        }

        val matchId = resolvedMatch.id
        val leagueId = resolvedMatch.leagueId

        val existingPred = predictionRepository.findByUserIdAndMatchId(localUserId, matchId)
        if (existingPred == null) {
            val newPrediction = PredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = localUserId,
                matchId = matchId,
                leagueId = leagueId,
                predictedHomeScore = scoreA,
                predictedAwayScore = scoreB,
                pointsAwarded = pointsAwarded,
                calculatedAt = if (isProcessed) updatedAt else null,
                isProcessed = isProcessed,
                createdAt = updatedAt,
                updatedAt = updatedAt
            )
            predictionRepository.save(newPrediction)
        } else {
            // Atualiza mantendo histórico
            val updated = PredictionJpaEntity(
                id = existingPred.id,
                userId = localUserId,
                matchId = matchId,
                leagueId = leagueId,
                predictedHomeScore = scoreA,
                predictedAwayScore = scoreB,
                pointsAwarded = pointsAwarded,
                calculatedAt = if (isProcessed) updatedAt else null,
                isProcessed = isProcessed,
                createdAt = existingPred.createdAt,
                updatedAt = updatedAt
            )
            predictionRepository.save(updated)
        }
        return true
    }

    private fun resolveMatch(
        matchIdStr: String,
        sortedMatches: List<MatchJpaEntity>,
        matchLookup: Map<String, Pair<String, String>>
    ): MatchJpaEntity? {
        // Caso 1: Se matchId for um UUID direto, busca por UUID
        try {
            val uuid = UUID.fromString(matchIdStr)
            val directMatch = sortedMatches.find { it.id == uuid }
            if (directMatch != null) return directMatch
        } catch (e: IllegalArgumentException) {
            // Ignora e continua
        }

        // Caso 1.5: Se matchId for um ID de texto do Firestore (ex: "match_102" ou "match_013"), gera o UUID determinístico
        val docUuid = UUID.nameUUIDFromBytes(matchIdStr.toByteArray())
        val docMatch = sortedMatches.find { it.id == docUuid }
        if (docMatch != null) return docMatch

        val numericStr = matchIdStr.replace("match_", "").trim()
        val numericUuid = UUID.nameUUIDFromBytes(numericStr.toByteArray())
        val numericMatch = sortedMatches.find { it.id == numericUuid }
        if (numericMatch != null) return numericMatch

        // Caso 2: Mapeamento baseado no mapa de confrontos (evitando fragilidade de kickoffTime FIFO)
        val canonicalTeams = matchLookup[matchIdStr] ?: matchLookup[matchIdStr.replace("match_", "").trim()]
        if (canonicalTeams != null) {
            val (homeCan, awayCan) = canonicalTeams
            val matchedFixtures = sortedMatches.filter {
                canonicalName(it.homeTeamName) == homeCan && canonicalName(it.awayTeamName) == awayCan
            }

            if (matchedFixtures.isEmpty()) return null
            if (matchedFixtures.size == 1) return matchedFixtures[0]

            // Tratamento de Ambiguidade de repetição de confrontos (ex: México x Suécia duas vezes)
            val cleanNumber = matchIdStr.replace("match_", "").trim().toIntOrNull() ?: 1
            return if (cleanNumber <= 7) {
                // Primeira ocorrência (Fase de Grupos)
                matchedFixtures.minByOrNull { it.kickoffTime }
            } else {
                // Ocorrência subsequente (Mata-mata)
                matchedFixtures.maxByOrNull { it.kickoffTime }
            }
        }
        return null
    }

    private fun buildMatchLookup(warnings: MutableList<String>): Map<String, Pair<String, String>> {
        val lookup = mutableMapOf<String, Pair<String, String>>()

        // Tenta buscar da coleção real no Firestore se estiver disponível
        if (firestore != null) {
            try {
                // Tentamos coleções prováveis: "matches", "fixtures", "partidas"
                var matchesCollection = firestore.collection("matches")
                var documents = matchesCollection.get().get().documents
                if (documents.isEmpty()) {
                    matchesCollection = firestore.collection("fixtures")
                    documents = matchesCollection.get().get().documents
                }
                if (documents.isEmpty()) {
                    matchesCollection = firestore.collection("partidas")
                    documents = matchesCollection.get().get().documents
                }

                if (documents.isNotEmpty()) {
                    log.info("Lendo {} partidas do Firestore para construir mapa de confrontos.", documents.size)
                    for (doc in documents) {
                        val docId = doc.id // Ex: "match_1", "1"
                        val home = doc.getString("homeTeam") ?: doc.getString("homeTeamName") ?: doc.getString("home_team") ?: doc.getString("home") ?: ""
                        val away = doc.getString("awayTeam") ?: doc.getString("awayTeamName") ?: doc.getString("away_team") ?: doc.getString("away") ?: ""
                        if (home.isNotBlank() && away.isNotBlank()) {
                            lookup[docId] = Pair(canonicalName(home), canonicalName(away))
                            // Também mapeamos a chave puramente numérica
                            val numericKey = docId.replace("match_", "").trim()
                            lookup[numericKey] = Pair(canonicalName(home), canonicalName(away))
                        }
                    }
                    return lookup
                }
            } catch (e: Exception) {
                log.warn("Falha ao carregar coleção de partidas do Firestore: {}. Usando mapa fallback de confrontos.", e.message)
            }
        }

        // Fallback robusto / Simulação: Mapeamento oficial dos 22 confrontos criados pelo WorldCupMockSyncService
        log.info("Construindo mapa de confrontos fallback para simulação (22 partidas do WorldCupMockSyncService).")
        val fallbackMatchups = listOf(
            Pair("México", "Suécia"),          // 1
            Pair("Estados Unidos", "Bolívia"), // 2
            Pair("Canadá", "Camarões"),        // 3
            Pair("Argentina", "Japão"),        // 4
            Pair("França", "Austrália"),       // 5
            Pair("Brasil", "Coreia do Sul"),   // 6
            Pair("Portugal", "Gana"),          // 7
            Pair("México", "Suécia"),          // 8 (Dezesseis-avos)
            Pair("Estados Unidos", "Camarões"), // 9
            Pair("Estados Unidos", "Itália"),   // 10
            Pair("Argentina", "Nigéria"),      // 11
            Pair("França", "Alemanha"),        // 12
            Pair("Inglaterra", "Senegal"),      // 13
            Pair("Brasil", "Colômbia"),        // 14
            Pair("Brasil", "Holanda"),         // 15
            Pair("Argentina", "Inglaterra"),   // 16
            Pair("Marrocos", "Portugal"),      // 17
            Pair("Espanha", "França"),         // 18
            Pair("Brasil", "França"),          // 19
            Pair("Argentina", "Marrocos"),     // 20
            Pair("Brasil", "Marrocos"),        // 21
            Pair("Argentina", "França")        // 22
        )

        for (i in fallbackMatchups.indices) {
            val numStr = (i + 1).toString()
            val matchKey = "match_$numStr"
            val canonicalPair = Pair(canonicalName(fallbackMatchups[i].first), canonicalName(fallbackMatchups[i].second))
            lookup[numStr] = canonicalPair
            lookup[matchKey] = canonicalPair
        }

        return lookup
    }

    private fun canonicalName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.lowercase()
            .replace(Regex("[áàâãä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòôõö]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun parseTimestamp(value: Any?): Instant {
        if (value == null) return Instant.now()
        if (value is com.google.cloud.Timestamp) {
            return Instant.ofEpochSecond(value.seconds, value.nanos.toLong())
        }
        if (value is String) {
            return try {
                Instant.parse(value)
            } catch (e: DateTimeParseException) {
                Instant.now()
            }
        }
        return Instant.now()
    }

    private fun executeSimulation(startTime: Long): MigrationSummaryDto {
        val warnings = mutableListOf<String>()

        // Se não houver partidas no banco local, criamos mock partidas para permitir a simulação do relacionamento
        var sortedMatches = matchRepository.findAll().sortedBy { it.kickoffTime }
        if (sortedMatches.isEmpty()) {
            val sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
            val leagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
            val seasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

            // Criamos os 22 confrontos idênticos à nossa tabela do WorldCupMockSyncService para garantir resolução de matching perfeita
            val fallbackMatchups = listOf(
                Pair("México", "Suécia"),          // 1
                Pair("Estados Unidos", "Bolívia"), // 2
                Pair("Canadá", "Camarões"),        // 3
                Pair("Argentina", "Japão"),        // 4
                Pair("França", "Austrália"),       // 5
                Pair("Brasil", "Coreia do Sul"),   // 6
                Pair("Portugal", "Gana"),          // 7
                Pair("México", "Suécia"),          // 8 (Dezesseis-avos)
                Pair("Estados Unidos", "Camarões"), // 9
                Pair("Estados Unidos", "Itália"),   // 10
                Pair("Argentina", "Nigéria"),      // 11
                Pair("França", "Alemanha"),        // 12
                Pair("Inglaterra", "Senegal"),      // 13
                Pair("Brasil", "Colômbia"),        // 14
                Pair("Brasil", "Holanda"),         // 15
                Pair("Argentina", "Inglaterra"),   // 16
                Pair("Marrocos", "Portugal"),      // 17
                Pair("Espanha", "França"),         // 18
                Pair("Brasil", "França"),          // 19
                Pair("Argentina", "Marrocos"),     // 20
                Pair("Brasil", "Marrocos"),        // 21
                Pair("Argentina", "França")        // 22
            )

            val newMatches = mutableListOf<MatchJpaEntity>()
            for (i in fallbackMatchups.indices) {
                val match = MatchJpaEntity(
                    id = UUID.randomUUID(),
                    sportId = sportId,
                    leagueId = leagueId,
                    seasonId = seasonId,
                    homeTeamName = fallbackMatchups[i].first,
                    awayTeamName = fallbackMatchups[i].second,
                    kickoffTime = Instant.now().minusSeconds((3600L * 24 * (22 - i))),
                    status = if (i < 20) com.ligadospalpites.sportsfeed.domain.models.MatchStatus.FINISHED else com.ligadospalpites.sportsfeed.domain.models.MatchStatus.SCHEDULED,
                    homeScore = if (i < 20) 2 else null,
                    awayScore = if (i < 20) 1 else null,
                    phase = if (i < 7) "Fase de Grupos" else if (i < 9) "Dezesseis-avos de Final" else if (i < 14) "Oitavas de Final" else if (i < 18) "Quartas de Final" else if (i < 20) "Semifinal" else if (i == 20) "Disputa do 3º Lugar" else "Grande Final"
                )
                newMatches.add(match)
            }
            matchRepository.saveAll(newMatches)
            sortedMatches = newMatches
            log.info("Simulação: Criadas {} partidas de simulação completas.", sortedMatches.size)
        }

        val matchLookup = buildMatchLookup(warnings)

        // 1. Simular Usuários
        val mockFirebaseUids = listOf("fb-uid-sim-1", "fb-uid-sim-2", "fb-uid-sim-3", "fb-uid-sim-4", "fb-uid-sim-5")
        val mockNames = listOf("Carlos Silva", "Beatriz Ramos", "Vinícius Júnior", "Amanda Oliveira", "Daniel Souza")
        val mockEmails = listOf("carlos@sim.com", "beatriz@sim.com", "vini@sim.com", "amanda@sim.com", "daniel@sim.com")

        var usersCreated = 0
        val localUserIds = mutableListOf<UUID>()

        for (i in mockFirebaseUids.indices) {
            val uid = mockFirebaseUids[i]
            val existing = userRepository.findByFirebaseUid(uid)
            if (existing == null) {
                val newUser = UserJpaEntity(
                    id = UUID.randomUUID(),
                    firebaseUid = uid,
                    email = mockEmails[i],
                    name = mockNames[i],
                    createdAt = Instant.now().minusSeconds(3600 * 48)
                )
                val saved = userRepository.save(newUser)
                localUserIds.add(saved.id)
                usersCreated++
            } else {
                localUserIds.add(existing.id)
            }
        }

        // 2. Simular Ligas / Grupos
        val mockFirestoreGroupIds = listOf("group_simulated_premium", "group_simulated_friends")
        val mockGroupNames = listOf("Liga dos Campeões Premium", "Palpites da Galera")
        val postgresGroupIds = mockFirestoreGroupIds.map { UUID.nameUUIDFromBytes(it.toByteArray()) }

        for (i in mockFirestoreGroupIds.indices) {
            val pgGroupId = postgresGroupIds[i]
            if (!groupRepository.existsById(pgGroupId)) {
                val newGroup = GroupJpaEntity(
                    id = pgGroupId,
                    name = mockGroupNames[i],
                    creatorId = localUserIds[0],
                    scoringRulesJson = "{\"pointsExactScore\":10,\"pointsCorrectWinner\":5,\"pointsGoalDifference\":3}",
                    createdAt = Instant.now().minusSeconds(3600 * 24)
                )
                groupRepository.save(newGroup)
            }
        }

        // 3. Simular Membros e Rankings no Redis
        var membersMigrated = 0
        val mockScores = listOf(10, 15, 5, 8, 20)
        val mockStageScores = listOf(5, 10, 3, 5, 12)
        val mockKnockoutScores = listOf(5, 5, 2, 3, 8)

        for (g in postgresGroupIds.indices) {
            val pgGroupId = postgresGroupIds[g]
            for (u in localUserIds.indices) {
                val uId = localUserIds[u]
                val score = mockScores[u]
                val stageScore = mockStageScores[u]
                val koScore = mockKnockoutScores[u]

                val memberEntity = GroupMemberJpaEntity(
                    groupId = pgGroupId,
                    userId = uId,
                    joinedAt = Instant.now().minusSeconds(3600 * 12),
                    accumulatedPoints = score
                )
                groupMemberRepository.save(memberEntity)

                // Redis Sorted Sets updates
                val overallKey = "leaderboard:group:$pgGroupId:overall"
                val groupStageKey = "leaderboard:group:$pgGroupId:group-stage"
                val knockoutKey = "leaderboard:group:$pgGroupId:knockout"

                redisTemplate.opsForZSet().add(overallKey, uId.toString(), score.toDouble())
                redisTemplate.opsForZSet().add(groupStageKey, uId.toString(), stageScore.toDouble())
                redisTemplate.opsForZSet().add(knockoutKey, uId.toString(), koScore.toDouble())

                // Global Leaderboard
                val globalKey = "leaderboard:global"
                redisTemplate.opsForZSet().incrementScore(globalKey, uId.toString(), score.toDouble())

                membersMigrated++
            }
        }

        // 4. Simular Palpites (Predictions)
        var predictionsMigrated = 0
        // Para cada usuário, criamos palpites para as partidas de simulação
        for (u in localUserIds.indices) {
            val uId = localUserIds[u]

            // Palpite para partida 1 (México x Suécia, número "1")
            val p1Success = processPredictionMap(
                mapOf(
                    "matchId" to "match_1",
                    "scoreA" to 2,
                    "scoreB" to 1,
                    "pointsAwarded" to 10,
                    "isProcessed" to true,
                    "updatedAt" to Instant.now().minusSeconds(3600 * 3)
                ),
                uId,
                sortedMatches,
                matchLookup,
                warnings
            )
            if (p1Success) predictionsMigrated++

            // Palpite para partida 2 (Estados Unidos x Bolívia, número "2")
            val p2Success = processPredictionMap(
                mapOf(
                    "matchId" to "match_2",
                    "scoreA" to 3,
                    "scoreB" to 1,
                    "pointsAwarded" to 10,
                    "isProcessed" to true,
                    "updatedAt" to Instant.now().minusSeconds(3600 * 2)
                ),
                uId,
                sortedMatches,
                matchLookup,
                warnings
            )
            if (p2Success) predictionsMigrated++
        }

        return MigrationSummaryDto(
            status = "COMPLETED",
            simulated = true,
            usersProcessed = mockFirebaseUids.size,
            usersCreated = usersCreated,
            groupsMigrated = mockFirestoreGroupIds.size,
            groupMembersMigrated = membersMigrated,
            predictionsMigrated = predictionsMigrated,
            executionTimeMs = System.currentTimeMillis() - startTime,
            warnings = listOf("Modo simulação ativado: os dados gravados foram gerados automaticamente para teste.")
        )
    }
}
