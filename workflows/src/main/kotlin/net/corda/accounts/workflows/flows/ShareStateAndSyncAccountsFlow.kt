package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.accountService
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap
import net.corda.node.services.keys.PublicKeyHashToExternalId

@InitiatingFlow
@StartableByRPC
@StartableByService
class ShareStateAndSyncAccountsFlow(private val state: StateAndRef<ContractState>, private val partyToShareWith: AbstractParty) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val wellKnownPartyFromAnonymous = serviceHub.identityService.wellKnownPartyFromAnonymous(partyToShareWith)
                ?: throw IllegalStateException("Party: $partyToShareWith is not a well known identity on this node")

        wellKnownPartyFromAnonymous.let {
            val txToSend = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
                    ?: throw IllegalStateException("Transaction: ${state.ref.txhash} was not found on this node")

            txToSend.let { txToSend ->
                val accountsInvolvedWithState = state.state.data.participants.map { participant ->
                    accountService.accountInfo(participant.owningKey) to serviceHub.identityService.certificateFromKey(participant.owningKey)
                }.filter { it.first != null && it.second != null }
                accountsInvolvedWithState.forEach { accountToShare ->
                    subFlow(ShareAccountWithParties(accountToShare.first!!, listOf(wellKnownPartyFromAnonymous)))
                }
                val sessionToSendTo = initiateFlow(wellKnownPartyFromAnonymous)
                if (accountsInvolvedWithState.isNotEmpty()) {
                    sessionToSendTo.send(accountsInvolvedWithState.size)
                    accountsInvolvedWithState.forEach { pair ->
                        sessionToSendTo.send(pair)
                    }
                }
                subFlow(SendTransactionFlow(sessionToSendTo, txToSend))
            }
        }

    }

}

@InitiatedBy(ShareStateAndSyncAccountsFlow::class)
class ReceiveStateAndSyncAccountsFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val numberOfAccounts = otherSideSession.receive<Int>().unwrap { it }
        for (it in 0 until numberOfAccounts) {
            val (account, certPath) = otherSideSession.receive<Pair<StateAndRef<AccountInfo>, PartyAndCertificate>>().unwrap { it }
            serviceHub.identityService.verifyAndRegisterIdentity(certPath)
            serviceHub.withEntityManager {
                serviceHub.withEntityManager {
                    persist(PublicKeyHashToExternalId(account.state.data.id, certPath.owningKey))
                }
            }
        }
        subFlow(ReceiveTransactionFlow(otherSideSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}