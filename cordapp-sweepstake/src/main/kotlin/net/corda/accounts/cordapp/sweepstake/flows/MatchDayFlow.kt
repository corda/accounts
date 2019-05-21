package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.contracts.TournamentContract
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.flows.RequestKeyForAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
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
        val initialiseScores = mapOf(teamA to 0, teamB to 0)
        log.info("${teamA.state.data.team.teamName} are playing ${teamB.state.data.team.teamName}.")
        val result = session.sendAndReceive<Map<StateAndRef<TeamState>, Int>>(BeginMatch(initialiseScores)).unwrap { it }

        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accountForTeamA = accountService.accountInfo(teamA.state.data.owningAccountId)
        val accountForTeamB = accountService.accountInfo(teamB.state.data.owningAccountId)

        val signingAccounts = listOfNotNull(accountForTeamA, accountForTeamB)
        val winningTeam = determineWinningTeam(result)
        val winningAccount = accountService.accountInfo(winningTeam.state.data.owningAccountId)

        val newOwner = subFlow(RequestKeyForAccountFlow(winningAccount!!.state.data))
        val requiredSigners =
                signingAccounts.map { it.state.data.accountHost.owningKey } + listOfNotNull(
                        teamA.state.data.owningKey, teamB.state.data.owningKey, newOwner.owningKey
                )
        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(teamA)
                .addInputState(teamB)
                .addOutputState(winningTeam.state.data.copy(owningKey = newOwner.owningKey))
                .addCommand(TournamentContract.MATCH_WON, requiredSigners)
                .addReferenceState(accountForTeamA!!.referenced())
                .addReferenceState(accountForTeamB!!.referenced())

        val locallySignedTx = serviceHub.signInitialTransaction(
                transactionBuilder,
                listOfNotNull(
                        //Somewhere is this god forsaken mess it's not happy.
                        //Could it be to do with the keys for the states being registered on other nodes in RequestKeysForAccountsFlow?
                        ourIdentity.owningKey,
                        teamA.state.data.owningKey,
                        teamB.state.data.owningKey,
                        winningTeam.state.data.owningKey
                )
        )

        val sessionForAccountToSendTo = initiateFlow(winningAccount.state.data.accountHost)
        val fullySignedExceptForNotaryTx = subFlow(CollectSignaturesFlow(locallySignedTx, listOf(session)))

        val signedTx = subFlow(
                FinalityFlow(
                        fullySignedExceptForNotaryTx,
                        listOf(sessionForAccountToSendTo).filter { sessionForAccountToSendTo.counterparty != serviceHub.myInfo.legalIdentities.first() })
        )

        val movedState = signedTx.coreTransaction.outRefsOfType(
                TeamState::class.java
        ).single()
        return movedState
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

    private fun TransactionBuilder.addReferenceState(referencedStateAndRef: ReferencedStateAndRef<AccountInfo>?): TransactionBuilder {
        referencedStateAndRef?.let {
            return this.addReferenceState(it)
        }
        return this
    }
}


class MatchDayHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val teamsAndScore = otherSession.receive<BeginMatch>().unwrap { it }.results
        val fullTime = generateScores(teamsAndScore)
        otherSession.send(fullTime)
    }

    /**
     * Thread safe method for generating random scores between 0 and 10.
     */
    private fun generateScores(teamAndScore: Map<StateAndRef<TeamState>, Int>): Map<StateAndRef<TeamState>, Int> {
        val newScores = teamAndScore.mapValues { it.value.plus(ThreadLocalRandom.current().nextInt(0, 10)) }
        return if (newScores.values.first() == newScores.values.last()) {
            generateScores(newScores)
        } else {
            newScores
        }
    }
}