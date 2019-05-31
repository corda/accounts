package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.contracts.TournamentContract
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.flows.RequestKeyForAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
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

        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)

        val accountForTeamA = accountService.accountInfo(teamA.state.data.owningKey!!)
        val accountForTeamB = accountService.accountInfo(teamB.state.data.owningKey!!)

        val signingAccounts = listOfNotNull(accountForTeamA, accountForTeamB)
        val winningAccount = accountService.accountInfo(winningTeam.state.data.owningKey!!)

        val newOwner = subFlow(RequestKeyForAccountFlow(winningAccount!!.state.data))
        val requiredSigners =
                signingAccounts.map { it.state.data.accountHost.owningKey } + listOfNotNull(
                        teamA.state.data.owningKey, teamB.state.data.owningKey, newOwner.owningKey, ourIdentity.owningKey
                )

        // OK so required signers are:
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
                .addReferenceState(accountForTeamA!!.referenced())
                .addReferenceState(accountForTeamB!!.referenced())

        val locallySignedTx = serviceHub.signInitialTransaction(
                transactionBuilder,
                listOfNotNull(
                        ourIdentity.owningKey
                )
        )

        val sessionForWinner = initiateFlow(winningAccount.state.data.accountHost)
        val sessionForTeamB = initiateFlow(accountForTeamB.state.data.accountHost)
        val sessionForTeamA = initiateFlow(accountForTeamA.state.data.accountHost)

        val fullySignedExceptForNotaryTx = subFlow(CollectSignaturesFlow(locallySignedTx, listOf(
                sessionForTeamA,
                sessionForTeamB,
                sessionForWinner
        )))

        val signedTx = subFlow(
                FinalityFlow(
                        fullySignedExceptForNotaryTx,
                        listOf(sessionForWinner, sessionForTeamA, sessionForTeamB).filter { sessionForWinner.counterparty != serviceHub.myInfo.legalIdentities.first() })
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