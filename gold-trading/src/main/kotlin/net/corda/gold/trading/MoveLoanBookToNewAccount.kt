package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*


@StartableByRPC
@StartableByService
@InitiatingFlow
class MoveLoanBookToNewAccount(val accountId: UUID, val loanBook: StateAndRef<LoanBook>) :
    FlowLogic<StateAndRef<LoanBook>>() {

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val currentHoldingAccount = loanBook.state.data.owningAccount?.let { accountService.accountInfo(it) }
        val accountInfoToMoveTo = accountService.accountInfo(accountId)


        if (accountInfoToMoveTo == null) {
            throw IllegalStateException()
        } else if (currentHoldingAccount == null && loanBook.state.data.owningAccount != null)
            throw IllegalStateException("Attempting to move a loan book from an account we do not know about")
        else {
            val transactionBuilder = TransactionBuilder()
            transactionBuilder.notary = loanBook.state.notary
            transactionBuilder.addInputState(loanBook)

            val requiredSigners = listOfNotNull(
                accountInfoToMoveTo.state.data.signingKey,
                accountInfoToMoveTo.state.data.accountHost.owningKey,
                currentHoldingAccount?.state?.data?.accountHost?.owningKey,
                currentHoldingAccount?.state?.data?.signingKey
            )

            transactionBuilder.addCommand(LoanBookContract.TRANSFER_TO_ACCOUNT, requiredSigners)
            transactionBuilder.addReferenceState(ReferencedStateAndRef(accountInfoToMoveTo))

            if (loanBook.state.data.owningAccount != null){
                transactionBuilder.addReferenceState(ReferencedStateAndRef(currentHoldingAccount!!))
            }

            transactionBuilder.addOutputState(loanBook.state.data.copy(owningAccount = accountInfoToMoveTo.state.data.signingKey))

            //sign with our node key AND the private key which corresponds with this account - it must be in our kms as we are the hosts of the original account
            val keysToSignWith =
                listOfNotNull(currentHoldingAccount?.state?.data?.signingKey, serviceHub.myInfo.legalIdentities.first().owningKey)

            val locallySignedTx = serviceHub.signInitialTransaction(
                transactionBuilder,
                keysToSignWith
            )

            val sessionForAccountToSendTo = initiateFlow(accountInfoToMoveTo.state.data.accountHost)
            sessionForAccountToSendTo.send(locallySignedTx)
            val signaturesFromCounterParty = sessionForAccountToSendTo.receive<List<TransactionSignature>>().unwrap { it }
            val signatures = locallySignedTx.sigs + signaturesFromCounterParty
            val fullySignedExceptForNotaryTx = SignedTransaction(locallySignedTx.txBits, signatures)

            val signedTx = subFlow(
                FinalityFlow(
                    fullySignedExceptForNotaryTx,
                    listOf(sessionForAccountToSendTo).filter { sessionForAccountToSendTo.counterparty != serviceHub.myInfo.legalIdentities.first() })
            )
            val movedState = signedTx.coreTransaction.outRefsOfType(
                LoanBook::class.java
            ).single()

            val toCarbonCopy = accountInfoToMoveTo.state.data.carbonCopyReivers + (currentHoldingAccount?.state?.data?.carbonCopyReivers ?: listOf())

            subFlow(CarbonCopyFlow(toCarbonCopy, signedTx))
            return movedState
        }
    }
}

@InitiatingFlow
class CarbonCopyFlow(val toCarbonCopy: List<Party>, val txToBroadcast: SignedTransaction) : FlowLogic<Unit>(){
    @Suspendable
    override fun call(): Unit {
        for (party in toCarbonCopy) {
            val session = initiateFlow(party)
            subFlow(SendTransactionFlow(session, txToBroadcast))
        }
    }
}

@InitiatedBy(CarbonCopyFlow::class)
class RecordCarbonCopyFlow(val otherSession: FlowSession): FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        val recievedTx = subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
        logger.info("Successfully recorded carcbon copy transaction: ${recievedTx.id}")
    }
}

@InitiatedBy(MoveLoanBookToNewAccount::class)
class AccountSigningResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        //this is always going to be the "receiving" side of the transaction
        val transactionToSign = otherSession.receive(SignedTransaction::class.java).unwrap { it }
        val goldBrick = transactionToSign.coreTransaction.outputsOfType(LoanBook::class.java).single()
        val signatureForTxAccount = serviceHub.createSignature(transactionToSign, goldBrick.owningAccount!!)
        val signatureForTxNode = serviceHub.createSignature(transactionToSign, serviceHub.myInfo.legalIdentities.first().owningKey)
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


