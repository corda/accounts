package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord

class ShareAccountInfoFlow(
        val accountInfo: StateAndRef<AccountInfo>,
        val recipients: List<FlowSession>
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val txHash = accountInfo.ref.txhash
        val transactionToSend = serviceHub.validatedTransactions.getTransaction(txHash)
                ?: throw FlowException("Can't find transaction with hash $txHash")
        recipients.forEach { session ->
            subFlow(SendTransactionFlow(session, transactionToSend))
        }
    }
}

class ShareAccountInfoHandlerFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = subFlow(ReceiveTransactionFlow(
                otherSideSession = otherSession,
                statesToRecord = StatesToRecord.ALL_VISIBLE
        ))
        transaction.coreTransaction.outputsOfType(AccountInfo::class.java).singleOrNull()
    }
}

// Initiating versions of the above flows.

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

@InitiatedBy(ShareAccountInfo::class)
class ShareAccountInfoHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ShareAccountInfoHandlerFlow(otherSession))
}
