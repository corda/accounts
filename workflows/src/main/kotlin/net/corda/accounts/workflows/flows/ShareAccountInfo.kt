package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party

@StartableByRPC
@StartableByService
@InitiatingFlow
// TODO: Need an initiating and non initiating version.
class ShareAccountInfo(
        val accountInfo: StateAndRef<AccountInfo>,
        val recipients: List<Party>
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val txHash = accountInfo.ref.txhash
        val transactionToSend = serviceHub.validatedTransactions.getTransaction(txHash)
                ?: throw FlowException("Can't find transaction with hash $txHash")
        recipients.forEach { recipient ->
            val session = initiateFlow(recipient)
            subFlow(SendTransactionFlow(session, transactionToSend))
        }
    }
}

