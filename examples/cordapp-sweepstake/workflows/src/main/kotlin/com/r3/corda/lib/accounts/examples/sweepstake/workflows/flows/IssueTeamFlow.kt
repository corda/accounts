package com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.contracts.TournamentContract
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.TeamState
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.transactions.TransactionBuilder

class IssueTeamFlow(
        private val sessions: Collection<FlowSession>,
        private val accountInfo: StateAndRef<AccountInfo>,
        private val team: WorldCupTeam) : FlowLogic<StateAndRef<TeamState>>() {

    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        val keyToUse = subFlow(RequestKeyForAccount(accountInfo.state.data)).owningKey
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addCommand(TournamentContract.ISSUE_TEAM, serviceHub.myInfo.legalIdentities.first().owningKey)
        txBuilder.addOutputState(TeamState(team, true, keyToUse))
        val signedTxLocally = serviceHub.signInitialTransaction(txBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, sessions.filterNot { it.counterparty.name == ourIdentity.name }))
        return finalizedTx.coreTransaction.outRefsOfType(TeamState::class.java).single()
    }
}

class IssueTeamHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSession))
    }
}