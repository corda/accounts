package net.corda.gold.trading.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.gold.trading.contracts.LoanBookContract
import net.corda.gold.trading.contracts.states.LoanBook
import java.security.PublicKey
import java.util.*
import java.util.concurrent.atomic.AtomicReference


@StartableByRPC
@StartableByService
@InitiatingFlow
class MoveLoanBookToNewAccount(
        private val accountIdToMoveTo: UUID,
        private val loanBook: StateAndRef<LoanBook>,
        private val carbonCopyReceivers: Collection<AccountInfo>?
) : FlowLogic<StateAndRef<LoanBook>>() {

    constructor(accountId: UUID, loanBook: StateAndRef<LoanBook>) : this(accountId, loanBook, null)

    private lateinit var keyToMoveTo: PublicKey

    @Suspendable
    override fun call(): StateAndRef<LoanBook> {
        val currentHoldingAccount = loanBook.state.data.owningAccount?.let { accountService.accountInfo(it) }
        val accountInfoToMoveTo = accountService.accountInfo(accountIdToMoveTo)


        if (accountInfoToMoveTo == null) {
            throw IllegalStateException("An account to move the loan to must be provided")
        } else if ((currentHoldingAccount == null && loanBook.state.data.owningAccount != null) && (loanBook.state.data.owningAccount?.equals(ourIdentity.owningKey) == false))
            throw IllegalStateException("Attempting to move a loan book from an account we do not know about")
        else {
            keyToMoveTo = if (accountInfoToMoveTo.state.data.host == ourIdentity) {
                serviceHub.createKeyForAccount(accountInfoToMoveTo.state.data).owningKey
            } else {
                subFlow(RequestKeyForAccount(accountInfoToMoveTo.state.data)).owningKey
            }
            val transactionBuilder = TransactionBuilder(loanBook.state.notary)
                    .addInputState(loanBook)
                    .addOutputState(loanBook.state.data.copy(owningAccount = keyToMoveTo))
                    .addCommand(LoanBookContract.TRANSFER_TO_ACCOUNT, listOfNotNull(keyToMoveTo, loanBook.state.data.owningAccount))

            val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder, listOfNotNull(loanBook.state.data.owningAccount, ourIdentity.owningKey))
            val sessionForAccountToSendTo = initiateFlow(accountInfoToMoveTo.state.data.host)
            val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx, sessionForAccountToSendTo, keyToMoveTo))
            val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)
            val fullySignedTx = subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
            val movedState = fullySignedTx.coreTransaction.outRefsOfType(
                    LoanBook::class.java
            ).single()
            // broadcasting the state to carbon copy receivers
            if (currentHoldingAccount != null) {
                subFlow(BroadcastToCarbonCopyReceiversFlow(currentHoldingAccount.state.data, movedState, carbonCopyReceivers))
            }
            return movedState
        }
    }
}

@InitiatedBy(MoveLoanBookToNewAccount::class)
class AccountSigningResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val accountMovedTo = AtomicReference<AccountInfo>()
        val transactionSigner = object : SignTransactionFlow(otherSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val keyStateMovedTo = stx.coreTransaction.outRefsOfType(LoanBook::class.java).first().state.data.owningAccount
                keyStateMovedTo?.let {
                    accountMovedTo.set(accountService.accountInfo(keyStateMovedTo)?.state?.data)
                }

                if (accountMovedTo.get() == null) {
                    throw IllegalStateException("Account to move to was not found on this node")
                }

            }
        }
        val transaction = subFlow(transactionSigner)
        if (otherSession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            val recievedTx = subFlow(
                    ReceiveFinalityFlow(
                            otherSession,
                            expectedTxId = transaction.id,
                            statesToRecord = StatesToRecord.ALL_VISIBLE
                    )
            )
            val accountInfo = accountMovedTo.get()
            if (accountInfo != null) {
                subFlow(BroadcastToCarbonCopyReceiversFlow(accountInfo, recievedTx.coreTransaction.outRefsOfType(LoanBook::class.java).first()))
            }
        }
    }
}


