package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap
import net.corda.node.services.keys.PublicKeyHashToExternalId
import java.security.PublicKey

/**
 * This flow shares all of the [AccountInfo]s for a [StateAndRef], as well as the [StateAndRef] itself with a specified
 * party.
 *
 * @property state the state to share
 * @property sessionToShareWith existing session with a receiving [Party]
 */
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
            val party = serviceHub.identityService.wellKnownPartyFromAnonymous(AnonymousParty(participant.owningKey))
            if (accountInfo != null && party != null) {
                // Map the participant key to the well known party resolved by this node
                accountInfo to mapOf(participant.owningKey to party)
            } else null
        }.filterNotNull()
        if (accountsInvolvedWithState.isNotEmpty()) {
            sessionToShareWith.send(accountsInvolvedWithState.size)
            accountsInvolvedWithState.forEach { pair ->
                subFlow(ShareAccountInfoFlow(pair.first, listOf(sessionToShareWith)))
                sessionToShareWith.send(pair.second)
            }
        } else {
            sessionToShareWith.send(0)
        }
        subFlow(SendTransactionFlow(sessionToShareWith, txToSend))
    }
}

/** Responder flow for [ShareStateAndSyncAccountsFlow]. */
class ReceiveStateAndSyncAccountsFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val numberOfAccounts = otherSideSession.receive<Int>().unwrap { it }
        for (it in 0 until numberOfAccounts) {
            val accountInfo = subFlow(ShareAccountInfoHandlerFlow(otherSideSession))
            val keyToParty = otherSideSession.receive<Map<PublicKey, Party>>().unwrap { it }
            val key = keyToParty.keys.first()
            val party = keyToParty.values.first()
            serviceHub.identityService.registerKeyToParty(key, party)
            serviceHub.withEntityManager {
                persist(PublicKeyHashToExternalId(accountInfo.linearId.id, key))
            }
        }
        subFlow(ReceiveTransactionFlow(otherSideSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}

// Initiating versions of the above flows.
/**
 * Initiating and startable by service and RPC version of [ShareStateAndSyncAccountsFlow].
 *
 * @property state the state to share
 * @property sessionToShareWith existing session with a receiving [Party]
 */
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

/** Responder flow for [ShareStateAndSyncAccounts]. */
@InitiatedBy(ShareStateAndSyncAccounts::class)
class ReceiveStateAndSyncAccounts(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveStateAndSyncAccountsFlow(otherSession))
    }
}