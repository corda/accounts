package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.TransactionBuilder

class MineBrickFlow(val accountToMineInto: StateAndRef<AccountInfo>) : FlowLogic<StateAndRef<LoanBook>>() {

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {

        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
        transactionBuilder.addCommand(LoanBookContract.MINE, serviceHub.myInfo.legalIdentities.first().owningKey)
        transactionBuilder.addOutputState(LoanBook(accountToMineInto.state.data.signingKey))
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder)
        return subFlow(FinalityFlow(signedTx, listOf())).coreTransaction.outRefsOfType(LoanBook::class.java).single()

    }

}