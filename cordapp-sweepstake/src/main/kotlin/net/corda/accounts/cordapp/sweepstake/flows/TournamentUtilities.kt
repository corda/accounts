package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.annotations.VisibleForTesting
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.workflow.utilities.tokenAmountWithIssuerCriteria
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.CordaInternal
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import java.io.File
import java.util.concurrent.ThreadLocalRandom

/**
 * Helper functions.
 */
@CordaInternal
@VisibleForTesting
fun generateTeamsFromFile(filePath: String): MutableList<WorldCupTeam> {
    return File(filePath).readLines().map { teamString ->
        WorldCupTeam(teamString, false)
    }.shuffled().toMutableList()
}

@CordaInternal
@VisibleForTesting
fun generateParticipantsFromFile(filePath: String): MutableList<Participant> {
    return File(filePath).readLines().map { playerName ->
        Participant(playerName, false)
    }.shuffled().toMutableList()
}

fun createParticipantsForTournament(): MutableList<Participant> {
    return mutableListOf(
            Participant("Oliver", false),
            Participant("Jack", false),
            Participant("Harry", false),
            Participant("Jacob", false),
            Participant("Charlie", false),
            Participant("Thomas", false),
            Participant("George", false),
            Participant("Oscar", false),
            Participant("James", false),
            Participant("William", false),
            Participant("Noah", false),
            Participant("Alfie", false),
            Participant("Joshua", false),
            Participant("Muhammad", false),
            Participant("Henry", false),
            Participant("Leo", false),
            Participant("Amelia", false),
            Participant("Olivia", false),
            Participant("Isla", false),
            Participant("Emily", false),
            Participant("Poppy", false),
            Participant("Ava", false),
            Participant("Isabella", false),
            Participant("Jessica", false),
            Participant("Lily", false),
            Participant("Sophie", false),
            Participant("Grace", false),
            Participant("Sophia", false),
            Participant("Mia", false),
            Participant("Evie", false),
            Participant("Ruby", false),
            Participant("Ella", false)
    )
}

fun createTeamsForTournament() : MutableList<WorldCupTeam> {
    return mutableListOf(
            WorldCupTeam("Russia", false),
            WorldCupTeam("Saudi Arabia", false),
            WorldCupTeam("Egypt", false),
            WorldCupTeam("Uruguay", false),
            WorldCupTeam("Portugal", false),
            WorldCupTeam("Spain", false),
            WorldCupTeam("Morocco", false),
            WorldCupTeam("Iran", false),
            WorldCupTeam("France", false),
            WorldCupTeam("Australia", false),
            WorldCupTeam("Peru", false),
            WorldCupTeam("Denmark", false),
            WorldCupTeam("Argentina", false),
            WorldCupTeam("Iceland", false),
            WorldCupTeam("Croatia", false),
            WorldCupTeam("Nigeria", false),
            WorldCupTeam("Brazil", false),
            WorldCupTeam("Switzerland", false),
            WorldCupTeam("Costa Rica", false),
            WorldCupTeam("Serbia", false),
            WorldCupTeam("Germany", false),
            WorldCupTeam("Mexico", false),
            WorldCupTeam("Sweden", false),
            WorldCupTeam("South Korea", false),
            WorldCupTeam("Belgium", false),
            WorldCupTeam("Panama", false),
            WorldCupTeam("Tunisia", false),
            WorldCupTeam("England", false),
            WorldCupTeam("Poland", false),
            WorldCupTeam("Senegal", false),
            WorldCupTeam("Colombia", false),
            WorldCupTeam("Japan", false)
    )
}

@CordaInternal
@VisibleForTesting
fun generateGroupIdsForAccounts(numOfAccounts: Int, numOfTeams: Int): List<Int> {
    require(numOfAccounts == numOfTeams)
    require(numOfAccounts % 4 == 0)

    val numberOfGroups = numOfAccounts / 4

    return IntRange(1, numberOfGroups).asIterable().toList()
}

@CordaInternal
@VisibleForTesting
fun splitAccountsIntoGroupsOfFour(accounts: List<StateAndRef<AccountInfo>>): List<List<StateAndRef<AccountInfo>>> {
    return accounts.withIndex().groupBy { it.index / 4 }.map { it.value.map { it.value } }
}

@CordaInternal
@VisibleForTesting
fun generateScores(teamAndScore: Map<StateAndRef<TeamState>, Int>): Map<StateAndRef<TeamState>, Int> {
    val newScores = teamAndScore.mapValues { it.value.plus(ThreadLocalRandom.current().nextInt(0, 10)) }
    return if (newScores.values.first() == newScores.values.last()) {
        generateScores(newScores)
    } else {
        newScores
    }
}

@CordaInternal
@VisibleForTesting
fun generateQuickWinner(teamA: StateAndRef<TeamState>, teamB: StateAndRef<TeamState>): StateAndRef<TeamState> {
    val result = Math.random()
    return if (result <= 0.5) {
        teamA
    } else {
        teamB
    }
}

/**
 * Tournament objects.
 */
@CordaSerializable
data class WorldCupTeam(val teamName: String, val isAssigned: Boolean)

@CordaSerializable
data class Participant(val playerName: String, val hasAccount: Boolean)

/**
 * Flow wrapper.
 */
@StartableByRPC
@InitiatingFlow
class IssueTeamWrapper(private val accountInfo: StateAndRef<AccountInfo>,
                       private val team: WorldCupTeam) : FlowLogic<StateAndRef<TeamState>>() {
    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        return (subFlow(IssueTeamFlow(setOf(initiateFlow(accountInfo.state.data.accountHost)), accountInfo, team)))
    }
}

@InitiatedBy(IssueTeamWrapper::class)
class IssueTeamResponse(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(IssueTeamHandler(otherSession))
    }

}

@StartableByRPC
class CreateAccountForPlayer(private val player: Participant) : FlowLogic<StateAndRef<AccountInfo>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.createAccount(player.playerName).getOrThrow()
    }
}

@StartableByRPC
class ShareAccountInfo(private val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accounts = accountService.allAccounts()
        accounts.forEach { account ->
            accountService.shareAccountInfoWithParty(account.state.data.accountId, otherParty).getOrThrow()
        }
    }
}

@StartableByRPC
class AssignAccountsToGroups(private val accounts: List<StateAndRef<AccountInfo>>,
                             private val numOfTeams: Int) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        serviceHub.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, numOfTeams)
    }
}

@StartableByRPC
class GetAccountGroupInfo : FlowLogic<List<StateAndRef<AccountGroup>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountGroup>> {
        return serviceHub.vaultService.queryBy<AccountGroup>().states
    }
}

@StartableByRPC
class GetPrizeWinners : FlowLogic<List<AbstractParty>>() {

    @Suspendable
    override fun call(): List<AbstractParty> {
        val issuerCriteria = tokenAmountWithIssuerCriteria(GBP, serviceHub.myInfo.legalIdentities.first())
        val tokens = serviceHub.vaultService.queryBy<FungibleToken<*>>(issuerCriteria).states
        return tokens.map {
            it.state.data.holder
        }
    }
}

@StartableByRPC
class GetTeamStates : FlowLogic<List<StateAndRef<TeamState>>>() {
    override fun call(): List<StateAndRef<TeamState>> {
        return serviceHub.vaultService.queryBy<TeamState>().states
    }

}
