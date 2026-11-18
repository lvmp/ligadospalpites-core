package com.ligadospalpites.sportsfeed.domain.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NbaTeamAliasResolverTest {

    @Test
    fun `should resolve common NBA team aliases across external APIs to canonical names`() {
        assertEquals("Los Angeles Clippers", NbaTeamAliasResolver.canonicalName("LA Clippers"))
        assertEquals("Los Angeles Clippers", NbaTeamAliasResolver.canonicalName("L.A. Clippers"))
        assertEquals("Los Angeles Clippers", NbaTeamAliasResolver.canonicalName("LAC"))
        assertEquals("Los Angeles Lakers", NbaTeamAliasResolver.canonicalName("LA Lakers"))
        assertEquals("Golden State Warriors", NbaTeamAliasResolver.canonicalName("GS Warriors"))
        assertEquals("Oklahoma City Thunder", NbaTeamAliasResolver.canonicalName("OKC Thunder"))
        assertEquals("San Antonio Spurs", NbaTeamAliasResolver.canonicalName("SA Spurs"))
        assertEquals("New Orleans Pelicans", NbaTeamAliasResolver.canonicalName("NO Pelicans"))
        assertEquals("New York Knicks", NbaTeamAliasResolver.canonicalName("NY Knicks"))
        assertEquals("Philadelphia 76ers", NbaTeamAliasResolver.canonicalName("76ers"))
        assertEquals("Brooklyn Nets", NbaTeamAliasResolver.canonicalName("BKN Nets"))
    }

    @Test
    fun `should resolve conferences for aliases and return null for unknown teams`() {
        assertEquals(NbaTeamAliasResolver.WESTERN_CONFERENCE, NbaTeamAliasResolver.resolveConference("LA Clippers"))
        assertEquals(NbaTeamAliasResolver.WESTERN_CONFERENCE, NbaTeamAliasResolver.resolveConference("L.A. Lakers"))
        assertEquals(NbaTeamAliasResolver.WESTERN_CONFERENCE, NbaTeamAliasResolver.resolveConference("OKC Thunder"))
        assertEquals(NbaTeamAliasResolver.EASTERN_CONFERENCE, NbaTeamAliasResolver.resolveConference("NY Knicks"))
        assertEquals(NbaTeamAliasResolver.EASTERN_CONFERENCE, NbaTeamAliasResolver.resolveConference("76ers"))
        assertNull(NbaTeamAliasResolver.resolveConference("Flamengo"))
    }
}
