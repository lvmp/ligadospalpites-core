package com.ligadospalpites.sportsfeed.domain.exceptions

open class SportsFeedDomainException(message: String) : RuntimeException(message)

class SportLockedException(
    message: String = "Assine o plano MULTI_SPORT para acessar este esporte."
) : SportsFeedDomainException(message)

class LeagueInactiveException(
    message: String = "Esta liga está inativa."
) : SportsFeedDomainException(message)
