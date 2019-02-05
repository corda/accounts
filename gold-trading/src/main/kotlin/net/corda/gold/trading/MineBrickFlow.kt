package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.TransactionBuilder

class MineBrickFlow : FlowLogic<StateAndRef<LoanBook>>() {

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {

        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
        transactionBuilder.addCommand(GoldBrickContract.MINE, serviceHub.myInfo.legalIdentities.first().owningKey)
        transactionBuilder.addOutputState(LoanBook())
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder)
        return subFlow(FinalityFlow(signedTx, listOf())).coreTransaction.outRefsOfType(LoanBook::class.java).single()

    }

}