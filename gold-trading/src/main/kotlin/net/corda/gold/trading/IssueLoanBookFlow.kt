package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.flows.ShareStateWithAccountFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@StartableByRPC
class IssueLoanBookFlow(val valueInUsd: Long, val accountToMineInto: StateAndRef<AccountInfo>? = null, val dealId: UUID = UUID.randomUUID()) : FlowLogic<StateAndRef<LoanBook>>() {
    constructor(valueInUsd: Long, accountToMineInto: StateAndRef<AccountInfo>) : this(valueInUsd, accountToMineInto, UUID.randomUUID())
    @Suspendable
    override fun call(): StateAndRef<LoanBook> {

        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
        transactionBuilder.addCommand(LoanBookContract.ISSUE, serviceHub.myInfo.legalIdentities.first().owningKey)
        transactionBuilder.addOutputState(LoanBook(dealId, valueInUsd, accountToMineInto?.state?.data?.signingKey))
        val signedTxLocally = serviceHub.signInitialTransaction(transactionBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, listOf()))
        val outputState = finalizedTx.coreTransaction.outRefsOfType(LoanBook::class.java).single()

        accountToMineInto?.let {
            accountToMineInto.state.data.carbonCopyReceivers.forEach {accountToNotify ->
                subFlow(ShareStateWithAccountFlow(accountToNotify, outputState))
            }
        }
        return outputState

    }

}