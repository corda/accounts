package com.r3.corda.lib.accounts.examples.sweepstake.service

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.flows.IssueAccountToGroupFlow
import com.r3.corda.lib.accounts.examples.sweepstake.flows.IssueTeamFlow
import com.r3.corda.lib.accounts.examples.sweepstake.flows.IssueTeamHandler
import com.r3.corda.lib.accounts.examples.sweepstake.flows.UpdateAccountGroupFlow
import com.r3.corda.lib.accounts.examples.sweepstake.states.AccountGroup
import com.r3.corda.lib.accounts.examples.sweepstake.states.TeamState
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import net.corda.core.CordaInternal
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import java.io.File
import java.util.*

@CordaService
class TournamentService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    @Suspendable
    fun assignAccountsToGroups(accounts: List<StateAndRef<AccountInfo>>, numOfTeams: Int, otherParty: Party) {
        val accountGroups = splitAccountsIntoGroupsOfFour(accounts)
        val groupIds = generateGroupIdsForAccounts(accounts.size, numOfTeams)
        val map = groupIds.zip(accountGroups)

        map.forEach {
            assignAccountsToGroup(it.first, it.second, otherParty)
        }
    }

    @Suspendable
    private fun assignAccountsToGroup(groupId: Int, accounts: List<StateAndRef<AccountInfo>>, otherParty: Party) {
        val initialState = flowAwareStartFlow(IssueAccountToGroupFlow(otherParty, accounts.first(), groupId)).toCompletableFuture().getOrThrow()
        updateStates(accounts.drop(1), initialState, otherParty)
    }

    @Suspendable
    private fun updateStates(accounts: List<StateAndRef<AccountInfo>>, initialState: StateAndRef<AccountGroup>, otherParty: Party) {
        while (accounts.isNotEmpty()) {
            val newState = flowAwareStartFlow(UpdateAccountGroupFlow(otherParty, accounts.first(), initialState.state.data.linearId)).toCompletableFuture().getOrThrow()
            return if (accounts.size == 1) {
            } else {
                updateStates(accounts.drop(1), newState, otherParty)
            }
        }
    }

    @Suspendable
    fun getAccountIdsForGroup(accountId: UUID): List<UUID> {
        val groupsContainingAccount = services.vaultService.queryBy<AccountGroup>().states.filter {
            it.state.data.accounts.contains(accountId)
        }

        return groupsContainingAccount.flatMap {
            it.state.data.accounts
        }
    }

    @Suspendable
    fun getTeamStates(): List<StateAndRef<TeamState>> {
        return services.vaultService.queryBy<TeamState>().states
    }

    @Suspendable
    fun getWinningTeamStates(): List<StateAndRef<TeamState>> {
        return services.vaultService.queryBy<TeamState>(QueryCriteria.LinearStateQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states
    }


    @Suspendable
    inline fun <reified T : Any> flowAwareStartFlow(flowLogic: FlowLogic<T>): CordaFuture<T> {
        val currentFlow = FlowLogic.currentTopLevel
        return if (currentFlow != null) {
            val result = currentFlow.subFlow(flowLogic)
            doneFuture(result)
        } else {
            this.services.startFlow(flowLogic).returnValue
        }
    }
}

/**
 * Helper functions.
 */
@CordaInternal
@VisibleForTesting
fun generateTeamsFromFile(filePath: String): MutableList<WorldCupTeam> {
    return File(filePath).readLines().map { teamString ->
        WorldCupTeam(teamString,false)
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

@StartableByRPC
class GetTeamStates : FlowLogic<List<StateAndRef<TeamState>>>() {
    override fun call(): List<StateAndRef<TeamState>> {
        return serviceHub.vaultService.queryBy<TeamState>().states
    }
}


@StartableByRPC
class GetTeamFromId(private val linearId: String) : FlowLogic<StateAndRef<TeamState>>() {
    override fun call(): StateAndRef<TeamState> {
        return serviceHub.vaultService.queryBy<TeamState>(QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(UniqueIdentifier.fromString(linearId)), status = Vault.StateStatus.UNCONSUMED)).states.first()
    }
}

@StartableByRPC
class GetWinningAccounts(private val winningTeams: List<StateAndRef<TeamState>>) : FlowLogic<List<StateAndRef<AccountInfo>>>() {

    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {
        val winningKeys = winningTeams.map {
            it.state.data.owningKey
        }.toList()

        val winningAccountIds = winningKeys.map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it!!)?.state?.data?.identifier?.id
        }.toList()

        val tournamentService = serviceHub.cordaService(TournamentService::class.java)

        val winningAccounts = winningAccountIds.flatMap {
            tournamentService.getAccountIdsForGroup(it!!)
        }.toSet().map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it)!!
        }
        return winningAccounts
    }
}