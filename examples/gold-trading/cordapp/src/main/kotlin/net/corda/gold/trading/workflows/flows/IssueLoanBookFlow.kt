package net.corda.gold.trading.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.gold.trading.contracts.LoanBookContract
import net.corda.gold.trading.contracts.states.LoanBook
import java.util.*

@StartableByRPC
class IssueLoanBookFlow(
        val valueInUsd: Long,
        val accountToMineInto: StateAndRef<AccountInfo>? = null,
        val dealId: UUID = UUID.randomUUID()
) : FlowLogic<StateAndRef<LoanBook>>() {

    constructor(valueInUsd: Long, accountToMineInto: StateAndRef<AccountInfo>) : this(valueInUsd, accountToMineInto, UUID.randomUUID())

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {

        val owningKey = accountToMineInto?.let {
            if (accountToMineInto.state.data.host == ourIdentity) {
                serviceHub.createKeyForAccount(accountToMineInto.state.data).owningKey
            } else {
                subFlow(RequestKeyForAccount(accountToMineInto.state.data)).owningKey
            }
        }

        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
        transactionBuilder.addCommand(LoanBookContract.ISSUE, owningKey?.let { listOf(it) }
                ?: listOf(ourIdentity.owningKey))
        transactionBuilder.addOutputState(LoanBook(dealId, valueInUsd, owningKey))

        val localSigned = owningKey?.let {
            serviceHub.signInitialTransaction(transactionBuilder, listOfNotNull(owningKey))
        } ?: serviceHub.signInitialTransaction(transactionBuilder)

        val finalizedTx = subFlow(FinalityFlow(localSigned, listOf()))
        return finalizedTx.coreTransaction.outRefsOfType(LoanBook::class.java).single()

    }

}