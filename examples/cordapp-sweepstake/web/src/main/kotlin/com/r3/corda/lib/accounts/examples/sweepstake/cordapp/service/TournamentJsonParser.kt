package com.r3.corda.lib.accounts.examples.sweepstake.cordapp.service

import com.beust.klaxon.JsonReader
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import net.corda.core.contracts.UniqueIdentifier
import java.io.StringReader

class TournamentJsonParser {

    companion object {

        fun getMatchResult(msg: String): MatchResult {
            var teamAId = ""
            var teamBId = ""
            var winningTeamId = ""

            JsonReader(StringReader(msg)).use { reader ->
                reader.beginObject {
                    while (reader.hasNext()) {
                        val readData = reader.nextName()
                        when (readData) {
                            "teamAId" -> teamAId = reader.nextString()
                            "teamBId" -> teamBId = reader.nextString()
                            "winningTeamId" -> winningTeamId = reader.nextString()
                            else -> throw IllegalArgumentException("The JSON response did not contain the required parameters.")
                        }
                    }
                }
            }
            return MatchResult(teamAId, teamBId, winningTeamId)
        }

        fun getTournamentResult(msg: String): TournamentResult {
            var firstPlaceId = ""
            var secondPlaceId = ""
            var thirdPlaceId = ""
            var fourthPlaceId = ""
            var prize = ""

            JsonReader(StringReader(msg)).use { reader ->
                reader.beginObject() {
                    while (reader.hasNext()) {
                        val readData = reader.nextName()
                        when (readData) {
                            "one" -> firstPlaceId = reader.nextString()
                            "two" -> secondPlaceId = reader.nextString()
                            "three" -> thirdPlaceId = reader.nextString()
                            "four" -> fourthPlaceId = reader.nextString()
                            "prize" -> prize = reader.nextString()
                            else -> throw IllegalArgumentException("The JSON response did not contain the required parameters.")
                        }
                    }
                }
            }
            return TournamentResult(firstPlaceId, secondPlaceId, thirdPlaceId, fourthPlaceId, prize.toLong())
        }
    }
}

data class MatchResult(val teamAId: String, val teamBId: String, val winningTeamId: String)

data class TournamentResult(val firstPlaceId: String, val secondPlaceId: String, val thirdPlaceId: String, val fourthPlaceId: String, val totalPrize: Long)

data class Team(val team: WorldCupTeam, val linearId: UniqueIdentifier)