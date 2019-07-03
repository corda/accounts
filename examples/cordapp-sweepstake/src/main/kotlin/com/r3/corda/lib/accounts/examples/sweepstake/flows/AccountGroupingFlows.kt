package com.r3.corda.lib.accounts.examples.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.TournamentContract
import com.r3.corda.lib.accounts.examples.sweepstake.states.AccountGroup
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
@StartableByService
class IssueAccountToGroupFlow(private val otherParty: Party,
                              private val account: StateAndRef<AccountInfo>,
                              private val groupId: Int) : FlowLogic<StateAndRef<AccountGroup>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountGroup> {
        val sessions = listOf(initiateFlow(otherParty))
        val keyToUse = subFlow(RequestKeyForAccount(account.state.data)).owningKey

        val outputState = AccountGroup("GROUP$groupId", listOf(account.state.data.identifier.id), keyToUse)
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addOutputState(outputState)
        txBuilder.addCommand(TournamentContract.ISSUE_GROUP, serviceHub.myInfo.legalIdentities.first().owningKey)
        val signedTxLocally = serviceHub.signInitialTransaction(txBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, sessions.filterNot { it.counterparty.name == ourIdentity.name }))
        return finalizedTx.coreTransaction.outRefsOfType(AccountGroup::class.java).single()
    }
}

@InitiatedBy(IssueAccountToGroupFlow::class)
class IssueGroupResponse(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSession))
    }
}

@InitiatingFlow
@StartableByRPC
@StartableByService
class UpdateAccountGroupFlow(private val otherParty: Party,
                             private val account: StateAndRef<AccountInfo>,
                             private val linearId: UniqueIdentifier) : FlowLogic<StateAndRef<AccountGroup>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountGroup> {
        val sessions = listOf(initiateFlow(otherParty))
        val newKey = subFlow(RequestKeyForAccount(account.state.data)).owningKey

        val inputState = getStateForLinearId(serviceHub, linearId)
        val groupAccountIds = inputState.state.data.accounts
        val outputState = inputState.state.data.copy(accounts = groupAccountIds.plus(account.state.data.identifier.id), owningKey = newKey)
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addInputState(inputState)
        txBuilder.addOutputState(outputState)
        //TODO fix sigs
        txBuilder.addCommand(TournamentContract.UPDATE_GROUP, serviceHub.myInfo.legalIdentities.first().owningKey)
        val signedTxLocally = serviceHub.signInitialTransaction(txBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, sessions.filterNot { it.counterparty.name == ourIdentity.name }))
        return finalizedTx.coreTransaction.outRefsOfType(AccountGroup::class.java).single()
    }
}


@InitiatedBy(UpdateAccountGroupFlow::class)
class UpdateGroupResponse(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSession))
    }
}

fun getStateForLinearId(serviceHub: ServiceHub, linearId: UniqueIdentifier): StateAndRef<AccountGroup> {
    return serviceHub.vaultService.queryBy<AccountGroup>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.first()
}
