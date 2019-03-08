package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.CollectSignaturesWithAccountsFlow
import net.corda.accounts.SignTransactionWithAccountsFlow
import net.corda.accounts.addCommand
import net.corda.accounts.flows.ShareStateWithAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.signInitialTransactionWithAccounts
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.TransactionBuilder
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
            val signingAccounts = listOfNotNull(accountInfoToMoveTo, currentHoldingAccount)

            val transactionBuilder = TransactionBuilder(loanBook.state.notary)
                    .addInputState(loanBook)
                    .addOutputState(loanBook.state.data.copy(owningAccount = accountInfoToMoveTo.state.data.signingKey))
                    .addCommand(LoanBookContract.TRANSFER_TO_ACCOUNT, signingAccounts)

            val locallySignedTx = serviceHub.signInitialTransactionWithAccounts(transactionBuilder)

            val sessionForAccountToSendTo = initiateFlow(accountInfoToMoveTo.state.data.accountHost)
            val fullySignedExceptForNotaryTx = subFlow(CollectSignaturesWithAccountsFlow(locallySignedTx, listOf(sessionForAccountToSendTo)))

            val signedTx = subFlow(
                FinalityFlow(
                    fullySignedExceptForNotaryTx,
                    listOf(sessionForAccountToSendTo).filter { sessionForAccountToSendTo.counterparty != serviceHub.myInfo.legalIdentities.first() })
            )

            val movedState = signedTx.coreTransaction.outRefsOfType(
                LoanBook::class.java
            ).single()

            // notifying observers
            val toCarbonCopy = accountInfoToMoveTo.state.data.carbonCopyReceivers + (currentHoldingAccount?.state?.data?.carbonCopyReceivers ?: listOf())
            toCarbonCopy.forEach {accountToNotify ->
                subFlow(ShareStateWithAccountFlow(accountToNotify, movedState))
            }

            return movedState
        }
    }

}

@InitiatedBy(MoveLoanBookToNewAccount::class)
class AccountSigningResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val tx = subFlow(SignTransactionWithAccountsFlow(otherSession))

        if (otherSession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            subFlow(
                ReceiveFinalityFlow(
                    otherSession,
                    expectedTxId = tx.id,
                    statesToRecord = StatesToRecord.ALL_VISIBLE
                )
            )
        }
    }

}