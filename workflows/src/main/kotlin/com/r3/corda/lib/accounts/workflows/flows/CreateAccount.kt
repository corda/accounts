package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.commands.Create
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * A flow to create a new account. The flow will fail if an account already exists with the provided [name] or [identifier].
 *
 * @property name the proposed name for the new account.
 * @property identifier the proposed identifier for the new account.
 * @property externalId the proposed external ID for the new account.
 */
@StartableByService
@StartableByRPC
class CreateAccount private constructor(
        private val name: String,
        private val identifier: UUID,
        private val externalId: String?
) : FlowLogic<StateAndRef<AccountInfo>>() {

    /** Create a new account with a specified [name] but generate a new random [id]. */
    constructor(name: String) : this(name, UUID.randomUUID(), null)

    /** Create a new account with a specified [name] and [externalId] but generate a new random [id]. */
    constructor(name: String, externalId: String? = null) : this(name, UUID.randomUUID(), externalId)

    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        // There might be another account on this node with the same name... That's OK as long as the host is another
        // node. This can happen because another node shared that account with us. However, there cannot be two accounts
        // with the same name with the same host node.
        require(accountService.accountInfo(name).none { it.state.data.host == ourIdentity }) {
            "There is already an account registered with the specified name $name."
        }
        require(accountService.accountInfo(identifier) == null) {
            "There is already an account registered with the specified identifier $identifier."
        }
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val newAccountInfo = AccountInfo(
                name = name,
                host = ourIdentity,
                identifier = UniqueIdentifier(id = identifier, externalId = externalId)
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