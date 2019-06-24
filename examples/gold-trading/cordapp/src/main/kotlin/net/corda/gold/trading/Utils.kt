package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.security.PublicKey

fun TransactionBuilder.addCommand(data: CommandData, accounts: List<StateAndRef<AccountInfo>>): TransactionBuilder {
    addCommand(data, accounts.flatMap { listOf(it.state.data.signingKey, it.state.data.accountHost.owningKey) }.toSet().toList())
    accounts.forEach { addReferenceState(ReferencedStateAndRef(it)) }
    return this
}

fun List<CommandWithParties<CommandData>>.signingKeys(): Set<PublicKey> = flatMap { it.signers }.toSet()


private fun LedgerTransaction.allSigningAccounts(): List<StateAndRef<AccountInfo>> {
    val allSigners = commands.signingKeys()
    return referenceInputRefsOfType<AccountInfo>()
            .filter { it.state.data.signingKey in allSigners }
}

private fun LedgerTransaction.ourSigningAccounts(serviceHub: ServiceHub): List<StateAndRef<AccountInfo>> {
    return allSigningAccounts()
            .filter { it.state.data.accountHost == serviceHub.myInfo.legalIdentities.first() }
}

fun ServiceHub.signInitialTransactionWithAccounts(builder: TransactionBuilder): SignedTransaction {
    val ledgerTx = builder.toLedgerTransaction(this)
    val ourSigningAccounts = ledgerTx.ourSigningAccounts(this)
    val keysToSignWith = ourSigningAccounts.map { it.state.data.signingKey } + myInfo.legalIdentities.first().owningKey
    val signedTx = signInitialTransaction(
            builder,
            keysToSignWith
    )
    return signedTx
}


class CollectSignaturesWithAccountsFlow(
        private val partiallySignedTx: SignedTransaction,
        private val sessionsToCollectFrom: List<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        partiallySignedTx.verify(serviceHub, false)
        val ledgerTx = partiallySignedTx.toLedgerTransaction(serviceHub, false)
        val ourSigningAccounts = ledgerTx.ourSigningAccounts(serviceHub)
        val theirSigningAccounts = ledgerTx.allSigningAccounts() - ourSigningAccounts
        val theirSigningAccountsByParty = theirSigningAccounts.groupBy { it.state.data.accountHost }

        // collecting signatures for all accounts involved
        val allAccountsSigs = theirSigningAccountsByParty.flatMap { entry ->
            val session = sessionsToCollectFrom.firstOrNull { session -> session.counterparty == entry.key }
                    ?: throw FlowException("Missing session for party ${entry.key}")
            subFlow(SendTransactionFlow(session, partiallySignedTx))
            val sigs = session.receive<List<TransactionSignature>>().unwrap { it }
            val expectedSigners = entry.value.map { it.state.data.signingKey } + session.counterparty.owningKey
            if (sigs.map { it.by }.toSet() != expectedSigners.toSet()) {
                throw FlowException("Counterparty ${entry.key} should have signed by all required accounts + his identity")
            }
            sigs
        }

        val accountsSignedTx = partiallySignedTx.withAdditionalSignatures(allAccountsSigs)

        // missing signers but the notary
        val missingSigners = accountsSignedTx.getMissingSigners() - partiallySignedTx.notary!!.owningKey

        // if some of the sigs are still missing - collecting them via CollectSignaturesFlow
        return if (missingSigners.isNotEmpty()) {
            val sessionsForMissingSigners = sessionsToCollectFrom.filter { it.counterparty.owningKey in missingSigners }
            if (sessionsForMissingSigners.size != missingSigners.size) {
                throw FlowException("Some sessions are missing")
            }
            subFlow(CollectSignaturesFlow(accountsSignedTx, sessionsForMissingSigners))
        } else {
            accountsSignedTx
        }
    }
}

// TODO: should be an abstract class similarly to SignTransactionFlow
abstract class SignTransactionWithAccountsFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //this is always going to be the "receiving" side of the transaction
        val transactionToSign = subFlow(ReceiveTransactionFlow(session, checkSufficientSignatures = false))
        val ledgerTx = transactionToSign.toLedgerTransaction(serviceHub, false)
        ledgerTx.verify()
        checkTransaction(transactionToSign)
        val ourAccounts = ledgerTx.ourSigningAccounts(serviceHub)
        val sigs = ourAccounts.map { serviceHub.createSignature(transactionToSign, it.state.data.signingKey) } + serviceHub.createSignature(transactionToSign, ourIdentity.owningKey)
        session.send(sigs)
        return transactionToSign.withAdditionalSignatures(sigs)
    }

    abstract fun checkTransaction(tx: SignedTransaction)
}