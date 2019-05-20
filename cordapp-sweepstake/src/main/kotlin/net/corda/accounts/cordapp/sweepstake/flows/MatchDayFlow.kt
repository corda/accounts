package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.contracts.TournamentContract
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.concurrent.ThreadLocalRandom

class MatchDayFlow(
        private val session: FlowSession,
        private val teamA: StateAndRef<TeamState>,
        private val teamB: StateAndRef<TeamState>) : FlowLogic<StateAndRef<TeamState>>() {

    companion object {
        private val log = contextLogger()
    }

    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        val initialScores = mapOf(teamA to 0, teamB to 0)
        log.info("${teamA.state.data.team.teamName} are playing ${teamB.state.data.team.teamName}.")
        val result = session.sendAndReceive<Map<StateAndRef<TeamState>, Int>>(BeginMatch(initialScores)).unwrap { it }

        val cordaService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accountForTeamA = cordaService.accountInfo(teamA.state.data.owningAccountId)
        val accountForTeamB = cordaService.accountInfo(teamB.state.data.owningAccountId)

        val signingAccounts = listOfNotNull(accountForTeamA, accountForTeamB)


        val outputState = determineWinningTeam(result)

        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(teamA)
                .addInputState(teamB)
                .addOutputState(teamB.state.data)
                .addCommand(TournamentContract.MATCH_WON, signingAccounts)
        val locallySignedTx = serviceHub.signInitialTransactionWithAccounts(transactionBuilder)

        val signedTxLocally = serviceHub.signInitialTransaction(transactionBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, listOf()))
        return finalizedTx.coreTransaction.outRefsOfType(TeamState::class.java).single()

//        val sessionForAccountToSendTo = initiateFlow(outputState.state)
//        val fullySignedExceptForNotaryTx = subFlow(CollectSignaturesWithAccountsFlow(locallySignedTx, listOf(sessionForAccountToSendTo)))
//
//        val signedTx = subFlow(
//                FinalityFlow(
//                        fullySignedExceptForNotaryTx,
//                        listOf(sessionForAccountToSendTo).filter { sessionForAccountToSendTo.counterparty != serviceHub.myInfo.legalIdentities.first() })
//        )
//
//        val movedState = signedTx.coreTransaction.outRefsOfType(
//                LoanBook::class.java
//        ).single()
//
//
//        val accountThatOwnedState = signingAccounts.firstOrNull { it.state.data.signingKey == loanBook.state.data.owningAccount }
//
//        // broadcasting the state to carbon copy receivers
//        if (accountThatOwnedState != null) {
//            subFlow(BroadcastToCarbonCopyReceiversFlow(accountThatOwnedState.state.data, movedState, carbonCopyReceivers))
//        }
//
//        return movedState
    }

    private fun determineWinningTeam(result: Map<StateAndRef<TeamState>, Int>): StateAndRef<TeamState> {
        val teamAScore = result[teamA]!!
        val teamBScore = result[teamB]!!

        log.info("${teamA.state.data.team.teamName}'s score is $teamAScore and ${teamB.state.data.team.teamName}'s score is " + "$teamBScore.")

        return if (teamBScore > teamAScore) {
            teamB
        } else {
            teamA
        }
    }

}


class MatchDayHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val teamsAndScore = otherSession.receive<BeginMatch>().unwrap { it }.results
        val fullTime = generateScores(teamsAndScore)
        otherSession.send(fullTime)
    }

    private fun generateScores(teamAndScore: Map<StateAndRef<TeamState>, Int>): Map<StateAndRef<TeamState>, Int> {
        val newScores = teamAndScore.mapValues { it.value.plus(ThreadLocalRandom.current().nextInt(0, 10)) }
        return if (newScores.values.first() == newScores.values.last()) {
            generateScores(newScores)
        } else {
            newScores
        }
    }
}

@CordaSerializable
class BeginMatch(val results: Map<StateAndRef<TeamState>, Int>)

fun TransactionBuilder.addCommand(data: CommandData, accounts: List<StateAndRef<AccountInfo>>): TransactionBuilder {
    addCommand(data, accounts.map { it.state.data.accountHost.owningKey })
    accounts.forEach { addReferenceState(ReferencedStateAndRef(it)) }
    return this
}

fun ServiceHub.signInitialTransactionWithAccounts(builder: TransactionBuilder): SignedTransaction {
    val ledgerTx = builder.toLedgerTransaction(this)
    val ourSigningAccounts = ledgerTx.ourSigningAccounts(this)
    val keysToSignWith = ourSigningAccounts.map { it.state.data.accountHost.owningKey } + myInfo.legalIdentities.first().owningKey
    return signInitialTransaction(
            builder,
            keysToSignWith)
}

private fun LedgerTransaction.ourSigningAccounts(serviceHub: ServiceHub): List<StateAndRef<AccountInfo>> {
    return allSigningAccounts()
            .filter { it.state.data.accountHost == serviceHub.myInfo.legalIdentities.first() }
}

private fun LedgerTransaction.allSigningAccounts(): List<StateAndRef<AccountInfo>> {
    val allSigners = commands.signingKeys()
    return referenceInputRefsOfType<AccountInfo>()
            .filter { it.state.data.accountHost.owningKey in allSigners }
}

fun List<CommandWithParties<CommandData>>.signingKeys(): Set<PublicKey> = flatMap { it.signers }.toSet()