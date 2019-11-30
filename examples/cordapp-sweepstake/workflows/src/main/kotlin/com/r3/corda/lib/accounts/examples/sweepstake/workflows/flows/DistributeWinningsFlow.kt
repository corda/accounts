package com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.TeamState
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.service.TournamentService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty

@StartableByRPC
class DistributeWinningsFlow(private val winningTeams: List<StateAndRef<TeamState>>,
                             private val prizeAmount: Long,
                             private val prizeCurrency: TokenType) : FlowLogic<List<AnonymousParty>>() {

    @Suspendable
    override fun call(): List<AnonymousParty> {
        val winningKeys = winningTeams.map {
            it.state.data.owningKey
        }.toList()

        val winningAccountIds = winningKeys.map {
            serviceHub.accountService.accountInfo(it!!)?.state?.data?.identifier
        }.toList()

        val tournamentService = serviceHub.cordaService(TournamentService::class.java)

        val winningAccounts = winningAccountIds.flatMap {
            tournamentService.getAccountIdsForGroup(it?.id!!)
        }.toSet().map {
            serviceHub.accountService.accountInfo(it)
        }

        val parties = winningAccounts.map { account ->
            account?.state?.data.let { acc ->
                subFlow(RequestKeyForAccount(accountInfo = acc!!))
            }
        }

        // TODO make division of winnings more realistic
        val prize = prizeAmount / winningAccounts.size
        parties.forEach {
            val tokens = listOf(prize of prizeCurrency issuedBy ourIdentity heldBy it)
            subFlow(IssueTokens(tokens))
        }

        return parties
    }
}