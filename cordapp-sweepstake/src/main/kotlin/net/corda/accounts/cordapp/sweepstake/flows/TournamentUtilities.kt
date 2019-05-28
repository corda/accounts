package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.annotations.VisibleForTesting
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.CordaInternal
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import java.io.File

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

@CordaInternal
@VisibleForTesting
fun generateGroupIdsForAccounts(numOfAccounts: Int, numOfTeams: Int): List<Int>{
    require(numOfAccounts == numOfTeams)
    require(numOfAccounts % 4 == 0)

    val numberOfGroups = numOfAccounts / 4

    return IntRange(1, numberOfGroups).asIterable().toList()
}

@CordaInternal
@VisibleForTesting
fun splitAccountsIntoGroupsOfFour(accounts: List<StateAndRef<AccountInfo>>): List<List<StateAndRef<AccountInfo>>>{
    return accounts.withIndex().groupBy { it.index / 4 }.map{ it.value.map { it.value }}
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
class IssueTeamResponse(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(IssueTeamHandler(otherSession))
    }

}

@StartableByRPC
@InitiatingFlow
class CreateAccountForPlayer(private val player: Participant) : FlowLogic<StateAndRef<AccountInfo>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.createAccount(player.playerName).getOrThrow()
    }
}

@StartableByRPC
@InitiatingFlow
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
@InitiatingFlow
class AssignAccountsToGroups(private val accounts: List<StateAndRef<AccountInfo>>,
                                      private val numOfTeams: Int,
                                      private val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        serviceHub.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, numOfTeams, otherParty)
    }
}

@StartableByRPC
@InitiatingFlow
class VerifyGroups : FlowLogic<List<StateAndRef<AccountGroup>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountGroup>> {
        return serviceHub.vaultService.queryBy<AccountGroup>().states
    }
}