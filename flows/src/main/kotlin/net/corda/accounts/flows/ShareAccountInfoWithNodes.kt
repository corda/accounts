package net.corda.accounts.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.service.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord

@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareAccountInfoWithNodes(val account: StateAndRef<AccountInfo>, val others: List<Party>) : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val txToSend = serviceHub.validatedTransactions.getTransaction(account.ref.txhash)

        txToSend?.let {
            for (other in others) {
                val session = initiateFlow(other)
                subFlow(SendTransactionFlow(session, txToSend))
            }
            return true
        }
        return false
    }

}

@InitiatedBy(ShareAccountInfoWithNodes::class)
class GetAccountInfo(val otherSession: FlowSession) : FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }

}