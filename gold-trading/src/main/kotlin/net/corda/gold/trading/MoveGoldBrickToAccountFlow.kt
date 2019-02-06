package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*


@StartableByRPC
@StartableByService
@InitiatingFlow
class MoveGoldBrickToAccountFlow(val accountId: UUID, val goldBrick: StateAndRef<LoanBook>) :
    FlowLogic<StateAndRef<LoanBook>>() {

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)


        val accountInfoToMoveTo = accountService.accountInfo(accountId)

        val inputTx = serviceHub.validatedTransactions.getTransaction(goldBrick.ref.txhash)


       goldBrick.state.data.owningAccount?.let { accountService.accountInfo(it) }

        if (goldBrick.state.data.owningAccount != null){

        }

        val accountInfoToMoveFrom = goldBrick.state.data.owningAccount


        if (accountInfoToMoveTo == null) {
            throw IllegalStateException()
        } else if (accountInfoToMoveFrom != null)
            throw IllegalStateException("Attempting to move a gold brick from an account we do not know about")
        else {
            val transactionBuilder = TransactionBuilder()
            transactionBuilder.notary = goldBrick.state.notary
            transactionBuilder.addInputState(goldBrick)

            val requiredSigners = listOfNotNull(
                accountInfoToMoveTo.state.data.signingKey,
                accountInfoToMoveTo.state.data.accountHost.owningKey,
                accountInfoToMoveFrom?.accountHost?.owningKey,
                accountInfoToMoveFrom?.signingKey
            )

            transactionBuilder.addCommand(LoanBookContract.TRANSFER_TO_ACCOUNT, requiredSigners)
            transactionBuilder.addReferenceState(ReferencedStateAndRef(accountInfoToMoveTo))
            transactionBuilder.addOutputState(goldBrick.state.data.copy(owningAccount = accountInfoToMoveTo.state.data))

            //sign with our node key AND the private key which corresponds with this account - it must be in our kms as we are the hosts of the original account
            val keysToSignWith =
                listOfNotNull(accountInfoToMoveFrom?.signingKey, serviceHub.myInfo.legalIdentities.first().owningKey)

            val locallySignedTx = serviceHub.signInitialTransaction(
                transactionBuilder,
                keysToSignWith
            )

            val sessionForAccountToSendTo = initiateFlow(accountInfoToMoveTo.state.data.accountHost)
            sessionForAccountToSendTo.send(locallySignedTx)
            val signaturesFromCounterParty = sessionForAccountToSendTo.receive<List<TransactionSignature>>().unwrap { it }
            val signatures = locallySignedTx.sigs + signaturesFromCounterParty
            val signedTx = SignedTransaction(locallySignedTx.txBits, signatures)

            return subFlow(
                FinalityFlow(
                    signedTx,
                    listOf(sessionForAccountToSendTo).filter { sessionForAccountToSendTo.counterparty != serviceHub.myInfo.legalIdentities.first() })
            ).coreTransaction.outRefsOfType(
                LoanBook::class.java
            ).single()
        }
    }
}

@InitiatedBy(MoveGoldBrickToAccountFlow::class)
class AccountSigningResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        //this is always going to be the "receiving" side of the transaction
        val transactionToSign = otherSession.receive(SignedTransaction::class.java).unwrap { it }

        val goldBrick = transactionToSign.coreTransaction.outputsOfType(LoanBook::class.java).single()
        val signatureForTxAccount = serviceHub.createSignature(transactionToSign, goldBrick.owningAccount!!.signingKey)
        val signatureForTxNode = serviceHub.createSignature(transactionToSign, goldBrick.owningAccount.accountHost.owningKey)
        otherSession.send(listOf(signatureForTxAccount, signatureForTxNode))
        if (otherSession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            subFlow(
                ReceiveFinalityFlow(
                    otherSession,
                    expectedTxId = transactionToSign.id,
                    statesToRecord = StatesToRecord.ALL_VISIBLE
                )
            )
        }
    }

}


