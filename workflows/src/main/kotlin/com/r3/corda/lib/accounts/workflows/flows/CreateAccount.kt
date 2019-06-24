package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.commands.Create
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * A flow to create a new account. The flow will fail if an account already exists with the provided [name] or [id].
 *
 * @property name the proposed name for the new account.
 * @property id the proposed id for the new account.
 */
@StartableByService
@StartableByRPC
class CreateAccount(
        private val name: String,
        private val id: UUID
) : FlowLogic<StateAndRef<AccountInfo>>() {

    /** Create a new account with a specified [name] but generate a new random [id]. */
    constructor(name: String) : this(name, UUID.randomUUID())

    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        synchronized(CreateAccount::class) {
            require(accountService.accountInfo(name) == null) {
                "There is already an account registered with the specified name $name."
            }
            require(accountService.accountInfo(id) == null) {
                "There is already an account registered with the specified id $id."
            }
        }
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val newAccountInfo = AccountInfo(
                name = name,
                host = ourIdentity,
                id = UniqueIdentifier(id = id)
        )
        val transactionBuilder = TransactionBuilder(notary = notary).apply {
            addOutputState(newAccountInfo)
            addCommand(Create(), ourIdentity.owningKey)
        }
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)
        val finalisedTransaction = subFlow(FinalityFlow(signedTransaction, emptyList()))
        return finalisedTransaction.coreTransaction.outRefsOfType<AccountInfo>().single()
    }
}