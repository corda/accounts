package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.workflows.accountService
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap
import net.corda.node.services.keys.PublicKeyHashToExternalId

class ShareStateAndSyncAccountsFlow(
        private val state: StateAndRef<ContractState>,
        private val sessionToShareWith: FlowSession
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val txToSend = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
                ?: throw IllegalStateException("Transaction: ${state.ref.txhash} was not found on this node")
        val accountsInvolvedWithState = state.state.data.participants.map { participant ->
            val accountInfo = accountService.accountInfo(participant.owningKey)
            val partyAndCertificate = serviceHub.identityService.certificateFromKey(participant.owningKey)
            accountInfo to partyAndCertificate
        }.filter { it.first != null && it.second != null }
        if (accountsInvolvedWithState.isNotEmpty()) {
            sessionToShareWith.send(accountsInvolvedWithState.size)
            accountsInvolvedWithState.forEach { pair ->
                subFlow(ShareAccountInfoFlow(pair.first!!, listOf(sessionToShareWith)))
                sessionToShareWith.send(pair.second!!)
            }
        } else {
            sessionToShareWith.send(0)
        }
        subFlow(SendTransactionFlow(sessionToShareWith, txToSend))
    }
}

class ReceiveStateAndSyncAccountsFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val numberOfAccounts = otherSideSession.receive<Int>().unwrap { it }
        for (it in 0 until numberOfAccounts) {
            val accountInfo = subFlow(ShareAccountInfoHandlerFlow(otherSideSession))
            val certPath = otherSideSession.receive<PartyAndCertificate>().unwrap { it }
            serviceHub.identityService.verifyAndRegisterIdentity(certPath)
            serviceHub.withEntityManager {
                persist(PublicKeyHashToExternalId(accountInfo!!.id, certPath.owningKey))
            }
        }
        subFlow(ReceiveTransactionFlow(otherSideSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}

// Initiating versions of the above flows.

@InitiatingFlow
@StartableByRPC
@StartableByService
class ShareStateAndSyncAccounts(
        private val state: StateAndRef<ContractState>,
        private val partyToShareWith: Party
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(partyToShareWith)
        subFlow(ShareStateAndSyncAccountsFlow(state, session))
    }
}

@InitiatedBy(ShareStateAndSyncAccounts::class)
class ReceiveStateAndSyncAccounts(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveStateAndSyncAccountsFlow(otherSession))
    }
}