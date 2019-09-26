package com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.annotations.VisibleForTesting
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.AccountGroup
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.TeamState
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.service.Participant
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.service.TournamentService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import net.corda.core.CordaInternal
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
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
 * Flow wrapper.
 */
@StartableByRPC
@InitiatingFlow
class IssueTeamWrapper(private val accountInfo: StateAndRef<AccountInfo>,
                       private val team: WorldCupTeam) : FlowLogic<StateAndRef<TeamState>>() {
    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        return (subFlow(IssueTeamFlow(setOf(initiateFlow(accountInfo.state.data.host)), accountInfo, team)))
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
            accountService.shareAccountInfoWithParty(account.state.data.identifier.id, otherParty).getOrThrow()
        }
    }
}

@StartableByRPC
class AssignAccountsToGroups(private val accounts: List<StateAndRef<AccountInfo>>,
                             private val numOfTeams: Int,
                             private val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        serviceHub.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, numOfTeams, otherParty)
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
        val tokens = serviceHub.vaultService.queryBy<FungibleToken>(issuerCriteria).states
        return tokens.map {
            it.state.data.holder
        }
    }
}