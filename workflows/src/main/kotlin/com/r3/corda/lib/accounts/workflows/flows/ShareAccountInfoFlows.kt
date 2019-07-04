package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord

/**
 * Shares an [AccountInfo] [StateAndRef] with the parties represented by the specified [FlowSession]s. The [AccountInfo]
 * is always stored using [StatesToRecord.ALL_VISIBLE].
 *
 * @property accountInfo the state and ref of the account info to share. It must be looked-up before hand.
 * @property recipients the flow sessions for the parties to receive the [AccountInfo] [StateAndRef].
 */
class ShareAccountInfoFlow(
        val accountInfo: StateAndRef<AccountInfo>,
        val recipients: List<FlowSession>
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val txHash = accountInfo.ref.txhash
        // We must use SendTransactionFlow as SendStateAndRefFlow doesn't let us override StatesToRecord.
        val transactionToSend = serviceHub.validatedTransactions.getTransaction(txHash)
                ?: throw FlowException("Can't find transaction with hash $txHash")
        recipients.forEach { session ->
            subFlow(SendTransactionFlow(session, transactionToSend))
        }
    }
}

/** Responder flow for [ShareAccountInfoFlow]. */
class ShareAccountInfoHandlerFlow(val otherSession: FlowSession) : FlowLogic<AccountInfo>() {
    @Suspendable
    override fun call(): AccountInfo {
        val transaction = subFlow(ReceiveTransactionFlow(
                otherSideSession = otherSession,
                statesToRecord = StatesToRecord.ALL_VISIBLE
        ))
        return transaction.coreTransaction.outputsOfType(AccountInfo::class.java).single()
    }
}

// Initiating versions of the above flows.

/**
 * Shares an [AccountInfo] [StateAndRef] with the supplied [Party]s. The [AccountInfo] is always stored using
 * [StatesToRecord.ALL_VISIBLE].
 *
 * @property accountInfo the state and ref of the account info to share. It must be looked-up before hand.
 * @property recipients the parties to receive the [AccountInfo] [StateAndRef].
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareAccountInfo(val accountInfo: StateAndRef<AccountInfo>, val recipients: List<Party>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val sessions = recipients.map { recipient -> initiateFlow(recipient) }
        return subFlow(ShareAccountInfoFlow(accountInfo, sessions))
    }
}

/** Responder flow for [ShareAccountInfo]. */
@InitiatedBy(ShareAccountInfo::class)
class ShareAccountInfoHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ShareAccountInfoHandlerFlow(otherSession))
    }
}
