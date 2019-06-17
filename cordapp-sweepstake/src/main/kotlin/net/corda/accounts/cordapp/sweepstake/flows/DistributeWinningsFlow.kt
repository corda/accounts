package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.shell.IssueTokens
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.workflows.flows.RequestKeyForAccountFlow
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty

@StartableByRPC
class DistributeWinningsFlow(private val winningTeams: List<StateAndRef<TeamState>>,
                             private val prizeAmount: Long,
                             private val prizeCurrency: FiatCurrency) : FlowLogic<List<AnonymousParty>>() {

    @Suspendable
    override fun call(): List<AnonymousParty> {
        val winningKeys = winningTeams.map {
            it.state.data.owningKey
        }.toList()

        val winningAccountIds = winningKeys.map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it!!)?.state?.data?.id
        }.toList()

        val tournamentService = serviceHub.cordaService(TournamentService::class.java)

        val winningAccounts = winningAccountIds.flatMap {
            tournamentService.getAccountIdsForGroup(it!!)
        }.toSet().map {
            serviceHub.cordaService(KeyManagementBackedAccountService::class.java).accountInfo(it)
        }

        val parties = winningAccounts.map { account ->
            account?.state?.data.let { acc ->
                subFlow(RequestKeyForAccountFlow(accountInfo = acc!!))
            }
        }

        // TODO make division of winnings more realistic
        val prize = prizeAmount / winningAccounts.size
        parties.forEach {
            subFlow(IssueTokens(prize of prizeCurrency issuedBy serviceHub.myInfo.legalIdentities.first() heldBy it))
        }

        return parties
    }
}