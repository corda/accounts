package net.corda.accounts.cordapp.sweepstake.service

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.cordapp.sweepstake.flows.IssueAccountToGroupFlow
import net.corda.accounts.cordapp.sweepstake.flows.UpdateAccountGroupFlow
import net.corda.accounts.cordapp.sweepstake.flows.generateGroupIdsForAccounts
import net.corda.accounts.cordapp.sweepstake.flows.splitAccountsIntoGroupsOfFour
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
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

        groupsContainingAccount.forEach {
            println(it.state.data.groupName)
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