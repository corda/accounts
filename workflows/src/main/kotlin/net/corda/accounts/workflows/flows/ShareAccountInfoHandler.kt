package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.node.StatesToRecord

@InitiatedBy(ShareAccountInfo::class)
class ShareAccountInfoHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = subFlow(ReceiveTransactionFlow(
                otherSideSession = otherSession,
                statesToRecord = StatesToRecord.ALL_VISIBLE
        ))
        transaction.coreTransaction.outputsOfType(AccountInfo::class.java).singleOrNull()
    }
}