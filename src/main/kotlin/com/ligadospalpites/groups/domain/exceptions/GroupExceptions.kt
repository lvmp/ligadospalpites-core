package com.ligadospalpites.groups.domain.exceptions

open class GroupDomainException(message: String) : RuntimeException(message)

class GroupNotFoundException(
    message: String = "Grupo não encontrado."
) : GroupDomainException(message)

class NotGroupAdminException(
    message: String = "Apenas o criador do grupo pode expulsar membros."
) : GroupDomainException(message)

class GroupMemberNotFoundException(
    message: String = "Membro não encontrado no grupo."
) : GroupDomainException(message)
