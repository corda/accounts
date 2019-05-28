package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.contracts.TournamentContract
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.flows.RequestKeyForAccountFlow
import net.corda.accounts.states.AccountInfo
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
        val keyToUse = subFlow(RequestKeyForAccountFlow(account.state.data)).owningKey

        val outputState = AccountGroup("GROUP$groupId", listOf(account.state.data.accountId), keyToUse)
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addOutputState(outputState)
        txBuilder.addCommand(TournamentContract.ASSIGN_GROUP, serviceHub.myInfo.legalIdentities.first().owningKey)
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
        val newKey = subFlow(RequestKeyForAccountFlow(account.state.data)).owningKey

        val inputState = getStateForLinearIdea(serviceHub, linearId)
        val groupAccountIds = inputState.state.data.accounts
        val outputState = inputState.state.data.copy(accounts = groupAccountIds.plus(account.state.data.accountId), owningKey = newKey)
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addInputState(inputState)
        txBuilder.addOutputState(outputState)
        //TODO fix sigs
        txBuilder.addCommand(TournamentContract.ASSIGN_GROUP, serviceHub.myInfo.legalIdentities.first().owningKey)
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

fun getStateForLinearIdea(serviceHub: ServiceHub, linearId: UniqueIdentifier): StateAndRef<AccountGroup> {
    val blah = serviceHub.vaultService.queryBy<AccountGroup>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
    val blahblah = blah.states

    val firstBlah = blahblah.first()
    return firstBlah
}
