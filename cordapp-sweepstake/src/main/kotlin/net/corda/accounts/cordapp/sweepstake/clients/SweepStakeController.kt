package net.corda.accounts.cordapp.sweepstake.clients

import com.beust.klaxon.JsonReader
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.accounts.workflows.flows.OurAccounts
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.io.StringReader

@RestController
class SweepStakeController(@Autowired private val rpc: NodeRPCConnection) {

    private val proxy = rpc.proxy

    @RequestMapping("/load-players/", method = [RequestMethod.GET])
    fun loadPLayers(): List<Participant> {
        return createParticipantsForTournament()
    }

    @RequestMapping("/create-accounts-issue-teams/", method = [RequestMethod.GET])
    fun createAccountsAndIssueTeams(): Map<String, String> {
        val accountsFromVault = proxy.startFlow(::OurAccounts).returnValue.getOrThrow()

        val participants = createParticipantsForTournament()
        var accounts = mutableListOf<StateAndRef<AccountInfo>>()

        if (accountsFromVault.isEmpty()) {
            participants.forEach {
                val newAccount = proxy.startFlow(::CreateAccountForPlayer, it).returnValue.getOrThrow()
                accounts.add(newAccount)
            }
        } else {
            accountsFromVault.forEach {
                accounts.add(it)
            }
        }

        val teams = createTeamsForTournament()

        val accountIdToTeamName = accounts.zip(teams).toMap()

        val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
        if (teamStates.isEmpty()) {
            accountIdToTeamName.forEach {
                proxy.startFlow(::IssueTeamWrapper, it.key, it.value).returnValue.getOrThrow()
            }
        }
        return accountIdToTeamName.mapKeys { it.key.state.data.id.toString() }.mapValues { it.value.teamName }
    }

    @RequestMapping("/issue-groups/", method = [RequestMethod.GET])
    fun assignGroups(){
        val groups = proxy.startFlow(::GetAccountGroupInfo).returnValue.getOrThrow()
        if (groups.isEmpty()) {
            val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
            val accountsFromVault = proxy.startFlow(::OurAccounts).returnValue.getOrThrow()
            proxy.startFlow(::AssignAccountsToGroups, accountsFromVault, teamStates.size)
        }
    }

    @RequestMapping("/load-teams/", method = [RequestMethod.GET])
    fun loadTeams(): List<Team> {
        val teams = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow().map { it.state.data }
        return teams.map { Team(it.team, it.linearId) }
    }

    @RequestMapping("/play-match/", method = [RequestMethod.POST])
    fun playMatch(@RequestBody msg: String): ResponseEntity<String> {
        val matchResult = getMatchResult(msg)
        val teamAId = UniqueIdentifier(matchResult.teamAId)
        val teamBId = UniqueIdentifier(matchResult.teamBId)
        val winningTeamId = UniqueIdentifier(matchResult.winningTeamId)

        val teamAState = proxy.startFlow(::GetTeamFromId, teamAId).returnValue.getOrThrow()
        val teamBState = proxy.startFlow(::GetTeamFromId, teamBId).returnValue.getOrThrow()
        val winningTeamState = proxy.startFlow(::GetTeamFromId, winningTeamId).returnValue.getOrThrow()

        proxy.startFlow(::MatchDayFlow,winningTeamState, teamAState, teamBState).returnValue.getOrThrow()

        return ResponseEntity.status(HttpStatus.CREATED).body("Winner of match between ${teamAState.state.data.team.teamName}" +
                "and ${teamBState.state.data.team.teamName} was ${winningTeamState.state.data.team.teamName}")
    }

    private fun getMatchResult(msg: String): MatchResult {
        var teamAId = ""
        var teamBId = ""
        var winningTeamId = ""

        JsonReader(StringReader(msg)).use { reader ->
            reader.beginObject() {
                while (reader.hasNext()) {
                    val readData = reader.nextName()
                    when (readData) {
                        "teamAId" -> teamAId = reader.nextString()
                        "teamBId" -> teamBId = reader.nextString()
                        "winningTeamId" -> winningTeamId = reader.nextString()
                        else -> throw IllegalArgumentException("")
                    }
                }
            }
        }
        return MatchResult(teamAId, teamBId, winningTeamId)
    }
}

data class MatchResult(val teamAId: String, val teamBId: String, val winningTeamId: String)

data class Team(val team: WorldCupTeam, val linearId: UniqueIdentifier)