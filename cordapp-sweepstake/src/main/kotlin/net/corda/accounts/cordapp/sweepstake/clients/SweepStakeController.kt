package net.corda.accounts.cordapp.sweepstake.clients

import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import net.corda.accounts.workflows.flows.OurAccounts
import net.corda.core.contracts.UniqueIdentifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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

        if(accountsFromVault.isEmpty()) {
        participants.forEach {
           val newAccount =  proxy.startFlow(::CreateAccountForPlayer, it).returnValue.getOrThrow()
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
    fun assignGroups() :String {
        val groups = proxy.startFlow(::GetAccountGroupInfo).returnValue.getOrThrow()
        if (groups.isEmpty()) {
            val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
            val accountsFromVault = proxy.startFlow(::OurAccounts).returnValue.getOrThrow()
            proxy.startFlow(::AssignAccountsToGroups, accountsFromVault, teamStates.size)
        }
        return "success"
    }

    @RequestMapping("/load-teams/", method = [RequestMethod.GET])
    fun loadTeams(): List<Team> {
        val teams = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow().map { it.state.data }
        return teams.map{ Team(it.team, it.linearId) }
    }

    @RequestMapping("/play-match", method = [RequestMethod.POST])
    fun playMatch(@RequestBody msg: String): ResponseEntity<String> {

        return  ResponseEntity.status(HttpStatus.CREATED).body("hello")
    }
}

data class MatchResult(val teamAID: String, val teamBID: String, val winningTeamID: String)

data class Team(val team: WorldCupTeam, val linearId: UniqueIdentifier)