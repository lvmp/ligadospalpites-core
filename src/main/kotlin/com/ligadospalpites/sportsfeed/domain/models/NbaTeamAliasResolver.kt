package com.ligadospalpites.sportsfeed.domain.models

/**
 * Resolves NBA team name variations across external providers (ESPN, stats.nba.com, balldontlie.io, API-Basketball)
 * into a canonical full team name and its official conference.
 */
object NbaTeamAliasResolver {

    const val EASTERN_CONFERENCE = "Eastern Conference"
    const val WESTERN_CONFERENCE = "Western Conference"

    private data class NbaTeamSpec(
        val canonicalName: String,
        val conference: String,
        val aliases: Set<String>
    )

    private val teams: List<NbaTeamSpec> = listOf(
        // Eastern Conference (15)
        NbaTeamSpec("Atlanta Hawks", EASTERN_CONFERENCE, setOf("atlanta hawks", "hawks", "atl hawks", "atl")),
        NbaTeamSpec("Boston Celtics", EASTERN_CONFERENCE, setOf("boston celtics", "celtics", "bos celtics", "bos")),
        NbaTeamSpec("Brooklyn Nets", EASTERN_CONFERENCE, setOf("brooklyn nets", "nets", "bkn nets", "brk nets", "bkn", "brk", "new jersey nets")),
        NbaTeamSpec("Charlotte Hornets", EASTERN_CONFERENCE, setOf("charlotte hornets", "hornets", "cha hornets", "cha", "cho")),
        NbaTeamSpec("Chicago Bulls", EASTERN_CONFERENCE, setOf("chicago bulls", "bulls", "chi bulls", "chi")),
        NbaTeamSpec("Cleveland Cavaliers", EASTERN_CONFERENCE, setOf("cleveland cavaliers", "cavaliers", "cavs", "cle cavaliers", "cle")),
        NbaTeamSpec("Detroit Pistons", EASTERN_CONFERENCE, setOf("detroit pistons", "pistons", "det pistons", "det")),
        NbaTeamSpec("Indiana Pacers", EASTERN_CONFERENCE, setOf("indiana pacers", "pacers", "ind pacers", "ind")),
        NbaTeamSpec("Miami Heat", EASTERN_CONFERENCE, setOf("miami heat", "heat", "mia heat", "mia")),
        NbaTeamSpec("Milwaukee Bucks", EASTERN_CONFERENCE, setOf("milwaukee bucks", "bucks", "mil bucks", "mil")),
        NbaTeamSpec("New York Knicks", EASTERN_CONFERENCE, setOf("new york knicks", "knicks", "ny knicks", "nyk")),
        NbaTeamSpec("Orlando Magic", EASTERN_CONFERENCE, setOf("orlando magic", "magic", "orl magic", "orl")),
        NbaTeamSpec("Philadelphia 76ers", EASTERN_CONFERENCE, setOf("philadelphia 76ers", "76ers", "sixers", "phi 76ers", "phila 76ers", "phi")),
        NbaTeamSpec("Toronto Raptors", EASTERN_CONFERENCE, setOf("toronto raptors", "raptors", "tor raptors", "tor")),
        NbaTeamSpec("Washington Wizards", EASTERN_CONFERENCE, setOf("washington wizards", "wizards", "was wizards", "wsh wizards", "was", "wsh")),

        // Western Conference (15)
        NbaTeamSpec("Dallas Mavericks", WESTERN_CONFERENCE, setOf("dallas mavericks", "mavericks", "mavs", "dal mavericks", "dal")),
        NbaTeamSpec("Denver Nuggets", WESTERN_CONFERENCE, setOf("denver nuggets", "nuggets", "den nuggets", "den")),
        NbaTeamSpec("Golden State Warriors", WESTERN_CONFERENCE, setOf("golden state warriors", "warriors", "gs warriors", "gsw")),
        NbaTeamSpec("Houston Rockets", WESTERN_CONFERENCE, setOf("houston rockets", "rockets", "hou rockets", "hou")),
        NbaTeamSpec("Los Angeles Clippers", WESTERN_CONFERENCE, setOf("los angeles clippers", "la clippers", "clippers", "lac")),
        NbaTeamSpec("Los Angeles Lakers", WESTERN_CONFERENCE, setOf("los angeles lakers", "la lakers", "lakers", "lal")),
        NbaTeamSpec("Memphis Grizzlies", WESTERN_CONFERENCE, setOf("memphis grizzlies", "grizzlies", "mem grizzlies", "mem")),
        NbaTeamSpec("Minnesota Timberwolves", WESTERN_CONFERENCE, setOf("minnesota timberwolves", "timberwolves", "wolves", "min timberwolves", "min")),
        NbaTeamSpec("New Orleans Pelicans", WESTERN_CONFERENCE, setOf("new orleans pelicans", "pelicans", "no pelicans", "nop")),
        NbaTeamSpec("Oklahoma City Thunder", WESTERN_CONFERENCE, setOf("oklahoma city thunder", "thunder", "okc thunder", "okc")),
        NbaTeamSpec("Phoenix Suns", WESTERN_CONFERENCE, setOf("phoenix suns", "suns", "phx suns", "phx", "pho")),
        NbaTeamSpec("Portland Trail Blazers", WESTERN_CONFERENCE, setOf("portland trail blazers", "trail blazers", "blazers", "por trail blazers", "por")),
        NbaTeamSpec("Sacramento Kings", WESTERN_CONFERENCE, setOf("sacramento kings", "kings", "sac kings", "sac")),
        NbaTeamSpec("San Antonio Spurs", WESTERN_CONFERENCE, setOf("san antonio spurs", "spurs", "sa spurs", "sas")),
        NbaTeamSpec("Utah Jazz", WESTERN_CONFERENCE, setOf("utah jazz", "jazz", "uta jazz", "uta"))
    )

    private val aliasToSpec: Map<String, NbaTeamSpec> = buildMap {
        teams.forEach { spec ->
            spec.aliases.forEach { alias ->
                put(alias, spec)
            }
        }
    }

    private val whitespaceRegex = Regex("\\s+")

    private fun normalize(rawName: String): String {
        return rawName
            .lowercase()
            .replace(".", "")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun canonicalName(rawName: String): String {
        val norm = normalize(rawName)
        return aliasToSpec[norm]?.canonicalName ?: rawName.trim()
    }

    fun resolveConference(rawName: String): String? {
        val norm = normalize(rawName)
        aliasToSpec[norm]?.let { return it.conference }
        return teams.firstOrNull { spec ->
            spec.aliases.any { alias -> alias.length > 3 && (norm.contains(alias) || alias.contains(norm)) }
        }?.conference
    }
}
