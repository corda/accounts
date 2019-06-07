package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord

@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareAccountWithParties(val account: StateAndRef<AccountInfo>, val others: List<Party>) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val txToSend = serviceHub.validatedTransactions.getTransaction(account.ref.txhash)
        txToSend?.let {
            for (other in others) {
                val session = initiateFlow(other)
                subFlow(SendTransactionFlow(session, txToSend))
            }
        }
    }

}

@InitiatedBy(ShareAccountWithParties::class)
class GetAccountInfo(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val receivedAccount =
                subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE)).coreTransaction.outputsOfType(AccountInfo::class.java).singleOrNull()
    }

}