package com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.contracts.TournamentContract
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.TeamState
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger

@InitiatingFlow
@StartableByRPC
class MatchDayFlow(
        private val winningTeam: StateAndRef<TeamState>,
        private val teamA: StateAndRef<TeamState>,
        private val teamB: StateAndRef<TeamState>) : FlowLogic<StateAndRef<TeamState>>() {

    companion object {
        private val log = contextLogger()
    }

    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        log.info("${teamA.state.data.team.teamName} are playing ${teamB.state.data.team.teamName}.")

        val accountService = serviceHub.accountService

        val accountForTeamA = accountService.accountInfo(teamA.state.data.owningKey!!)
                ?: throw FlowException("Could not find account with public key: ${teamA.state.data.owningKey!!.toStringShort()}")
        val accountForTeamB = accountService.accountInfo(teamB.state.data.owningKey!!)
                ?: throw FlowException("Could not find account with public key: ${teamB.state.data.owningKey!!.toStringShort()}")

        val winningAccount = accountService.accountInfo(winningTeam.state.data.owningKey!!)
                ?: throw FlowException("Could not find account with public key: ${winningTeam.state.data.owningKey!!.toStringShort()}")

        val newOwner = if (winningAccount.state.data.host == ourIdentity) {
            serviceHub.createKeyForAccount(winningAccount.state.data)
        } else {
            subFlow(RequestKeyForAccount(winningAccount.state.data))
        }

        val signingAccounts = listOfNotNull(accountForTeamA, accountForTeamB)
        val requiredSigners =
                signingAccounts.map { it.state.data.host.owningKey } + listOfNotNull(
                        teamA.state.data.owningKey, teamB.state.data.owningKey, newOwner.owningKey, ourIdentity.owningKey
                )

        // Required signers are:
        // 1.) us (match provider) - provided by initialSignature
        // 2.) hosts of both accounts
        // 3.) owner of team A (hosted by accountForTeamA.host)
        // 4.) owner of team B (hosted by accountForTeamB.host)
        // 5.) newOwner of winning team (hosted by winningAccount.host)

        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(teamA)
                .addInputState(teamB)
                .addOutputState(winningTeam.state.data.copy(owningKey = newOwner.owningKey, isStillPlaying = true))
                .addCommand(TournamentContract.MATCH_WON, requiredSigners)
                .addReferenceState(accountForTeamA.referenced())
                .addReferenceState(accountForTeamB.referenced())

        val locallySignedTx = serviceHub.signInitialTransaction(
                transactionBuilder,
                listOfNotNull(
                        ourIdentity.owningKey
                )
        )

        // Use a set to avoid duplicate flow sessions
        val accountHosts = setOf(winningAccount.state.data.host, accountForTeamA.state.data.host, accountForTeamB.state.data.host)
        val sessions = accountHosts.map { initiateFlow(it) }

        val fullySignedExceptForNotaryTx = subFlow(CollectSignaturesFlow(locallySignedTx, sessions))

        val signedTx = subFlow(
                FinalityFlow(
                        fullySignedExceptForNotaryTx,
                        sessions.filter { winningAccount.state.data.host != serviceHub.myInfo.legalIdentities.first() })
        )

        return signedTx.coreTransaction.outRefsOfType(
                TeamState::class.java
        ).single()
    }

}

@InitiatedBy(MatchDayFlow::class)
class MatchDayHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val transactionSigner = object : SignTransactionFlow(otherSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                println("Signing on behalf of ${ourIdentity.name}")
            }
        }
        val transaction = subFlow(transactionSigner)
        if (otherSession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            subFlow(
                    ReceiveFinalityFlow(
                            otherSession,
                            expectedTxId = transaction.id,
                            statesToRecord = StatesToRecord.ALL_VISIBLE
                    )
            )
        }
    }
}