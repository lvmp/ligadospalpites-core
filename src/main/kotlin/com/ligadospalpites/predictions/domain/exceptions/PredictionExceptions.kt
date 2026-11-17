package com.ligadospalpites.predictions.domain.exceptions

open class PredictionDomainException(message: String) : RuntimeException(message)

class PredictionLockedException(
    message: String = "Este jogo já iniciou. Palpites trancados!"
) : PredictionDomainException(message)

class PredictionProcessedException(
    message: String = "Este palpite já foi liquidado e não pode ser alterado."
) : PredictionDomainException(message)

class SpecialPredictionLockedException(
    message: String = "O torneio já começou. Palpites de longo prazo trancados!"
) : PredictionDomainException(message)

class MatchNotFoundException(
    message: String = "Partida não encontrada."
) : PredictionDomainException(message)

class LeagueNotFoundException(
    message: String = "Liga não encontrada."
) : PredictionDomainException(message)
