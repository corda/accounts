package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.flows.RequestKeyForAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty

@StartableByRPC
class DistributeWinningsFlow(private val winningTeams: List<StateAndRef<TeamState>>,
                             private val prizeWinnings: Double): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val winningKeys = winningTeams.map {
            it.state.data.owningKey
        }.toList()

        val winningAccountIds = winningKeys.map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it!!)?.state?.data?.accountId
        }.toList()

        val tournamentService = serviceHub.cordaService(TournamentService::class.java)

        val winningAccounts = winningAccountIds.flatMap {
            tournamentService.getAccountIdsForGroup(it!!)
        }.toSet().map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it)
        }

        val parties = winningAccounts.map {
            it?.state?.data.let {
                subFlow(RequestKeyForAccountFlow(accountInfo = it!!))
            }
        }

        // Issue the prize money to the account

        // TODO make division of winnings more realistic
        val prize = prizeWinnings / winningAccounts.size

    }
}