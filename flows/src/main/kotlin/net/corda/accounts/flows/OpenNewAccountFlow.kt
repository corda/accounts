package net.corda.accounts.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.AccountInfoContract
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@StartableByService
@StartableByRPC
class OpenNewAccountFlow(private val id: String, private val accountId: UUID) : FlowLogic<StateAndRef<AccountInfo>>() {
    constructor(id: String) : this(id, UUID.randomUUID())

    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
        val newAccount = AccountInfo(id, serviceHub.myInfo.legalIdentities.first(), accountId)
        transactionBuilder.addOutputState(newAccount)
        transactionBuilder.addCommand(AccountInfoContract.OPEN, serviceHub.myInfo.legalIdentities.first().owningKey)
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder)
        return subFlow(FinalityFlow(signedTx, emptyList())).coreTransaction.outRefsOfType<AccountInfo>()
            .single()
    }

}