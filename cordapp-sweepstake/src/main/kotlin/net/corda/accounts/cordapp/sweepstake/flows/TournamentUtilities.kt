package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.annotations.VisibleForTesting
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.states.AccountInfo
import net.corda.core.CordaInternal
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import java.io.File
import java.util.*

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
class BeginMatch(val results: Map<StateAndRef<TeamState>, Int>)


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