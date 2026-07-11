package com.ligadospalpites.predictions.domain.ports

import com.ligadospalpites.predictions.domain.models.Prediction
import java.util.UUID

interface PredictionRepository {
    fun findById(id: UUID): Prediction?
    fun findByMatchId(matchId: UUID): List<Prediction>
    fun save(prediction: Prediction): Prediction
}
