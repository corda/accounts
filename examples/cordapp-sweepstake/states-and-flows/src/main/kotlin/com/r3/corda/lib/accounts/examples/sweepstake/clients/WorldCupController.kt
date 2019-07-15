package com.r3.corda.lib.accounts.examples.sweepstake.clients

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.flows.DistributeWinningsFlow
import com.r3.corda.lib.accounts.examples.sweepstake.flows.MatchDayFlow
import com.r3.corda.lib.accounts.examples.sweepstake.service.*
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.tokens.money.GBP
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class WorldCupController(@Autowired private val rpc: NodeRPCConnection) {

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
        return accountIdToTeamName.mapKeys { it.key.state.data.identifier.id.toString() }.mapValues { it.value.teamName }
    }

    @RequestMapping("/issue-groups/", method = [RequestMethod.GET])
    fun assignGroups(): ResponseEntity<String> {
        val groups = proxy.startFlow(::GetAccountGroupInfo).returnValue.getOrThrow()
        if (groups.isEmpty()) {
            val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
            val accountsFromVault = proxy.startFlow(::OurAccounts).returnValue.getOrThrow()
            val otherParty = proxy.partiesFromName("TournamentB", false)
            return if (otherParty.isNotEmpty()) {
                proxy.startFlow(::AssignAccountsToGroups, accountsFromVault, teamStates.size, otherParty.first())
                ResponseEntity.status(HttpStatus.CREATED).body("Groups were successfully assigned to the " +
                        "participants")
            } else {
                ResponseEntity.status(HttpStatus.NO_CONTENT).body("The counterparty required to assign the groups" +
                        "was not found")
            }
        }
        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("The participants have already been assigned" +
                "to groups.")
    }

    @RequestMapping("/load-teams/", method = [RequestMethod.GET])
    fun loadTeams(): List<Team> {
        val teams = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow().map { it.state.data }
        return teams.map { Team(it.team, it.linearId) }
    }

    @RequestMapping("/play-match/", method = [RequestMethod.POST])
    fun playMatch(@RequestBody msg: String): ResponseEntity<String> {
        val matchResult = TournamentJsonParser.getMatchResult(msg)

        val teamAState = proxy.startFlow(::GetTeamFromId, matchResult.teamAId).returnValue.getOrThrow()
        val teamBState = proxy.startFlow(::GetTeamFromId, matchResult.teamBId).returnValue.getOrThrow()
        val winningTeamState = proxy.startFlow(::GetTeamFromId, matchResult.winningTeamId).returnValue.getOrThrow()

        proxy.startFlow(::MatchDayFlow, winningTeamState, teamAState, teamBState).returnValue.getOrThrow()

        return ResponseEntity.status(HttpStatus.CREATED).body("Winner of match between ${teamAState.state.data.team.teamName}" +
                "and ${teamBState.state.data.team.teamName} was ${winningTeamState.state.data.team.teamName}")
    }

    @RequestMapping("/distribute-winnings/", method = [RequestMethod.POST])
    fun distributeWinnings(@RequestBody msg: String): List<String> {
        val tournamentResult = TournamentJsonParser.getTournamentResult(msg)

        val firstTeam = proxy.startFlow(::GetTeamFromId, tournamentResult.firstPlaceId).returnValue.getOrThrow()
        val secondTeam = proxy.startFlow(::GetTeamFromId, tournamentResult.secondPlaceId).returnValue.getOrThrow()
        val thirdTeam = proxy.startFlow(::GetTeamFromId, tournamentResult.thirdPlaceId).returnValue.getOrThrow()
        val fourthTeam = proxy.startFlow(::GetTeamFromId, tournamentResult.fourthPlaceId).returnValue.getOrThrow()
        val winningTeams = listOf(firstTeam, secondTeam, thirdTeam, fourthTeam)

        proxy.startFlow(::DistributeWinningsFlow, winningTeams, tournamentResult.totalPrize, GBP).returnValue.getOrThrow()

        val winningAccounts = proxy.startFlow(::GetWinningAccounts, winningTeams).returnValue.getOrThrow()
        return winningAccounts.map { it.state.data.identifier.id.toString() }
    }
}
