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
class MoveGoldBrickToAccountFlow(val accountId: UUID, val goldBrick: StateAndRef<GoldBrick>) :
    FlowLogic<StateAndRef<GoldBrick>?>() {

    @Suspendable
    override fun call(): StateAndRef<GoldBrick> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accountInfo = accountService.accountInfo(accountId)
        if (accountInfo == null) {
            throw IllegalStateException()
        } else {
            val transactionBuilder = TransactionBuilder()
            transactionBuilder.notary = goldBrick.state.notary
            transactionBuilder.addInputState(goldBrick)
            transactionBuilder.addCommand(GoldBrickContract.TRANSFER_TO_ACCOUNT, accountInfo.state.data.signingKey)
            transactionBuilder.addReferenceState(ReferencedStateAndRef(accountInfo))
            transactionBuilder.addOutputState(goldBrick.state.data.copy(owningAccount = accountInfo.state.data))

            val locallySignedTx =
                serviceHub.signInitialTransaction(transactionBuilder, serviceHub.myInfo.legalIdentities.first().owningKey)

            val session = initiateFlow(accountInfo.state.data.accountHost)
            session.send(locallySignedTx)
            val signatureFromCounterParty = session.receive(TransactionSignature::class.java).unwrap { it }
            val signatures = locallySignedTx.sigs + listOf(signatureFromCounterParty)
            val signedTx = SignedTransaction(locallySignedTx.txBits, signatures)

            return subFlow(
                FinalityFlow(
                    signedTx,
                    listOf(session).filter { session.counterparty != serviceHub.myInfo.legalIdentities.first() })
            ).coreTransaction.outRefsOfType(GoldBrick::class.java)
                .single()
        }
    }
}

@InitiatedBy(MoveGoldBrickToAccountFlow::class)
class AccountSigningResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val transactionToSign = otherSession.receive(SignedTransaction::class.java).unwrap { it }
        val goldBrick = transactionToSign.coreTransaction.outputsOfType(GoldBrick::class.java).single()
        val signatureForTx = serviceHub.createSignature(transactionToSign, goldBrick.owningAccount!!.signingKey)
        otherSession.send(signatureForTx)
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


