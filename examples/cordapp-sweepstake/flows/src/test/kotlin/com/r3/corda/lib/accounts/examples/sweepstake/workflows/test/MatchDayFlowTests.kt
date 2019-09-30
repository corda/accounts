package com.r3.corda.lib.accounts.examples.sweepstake.workflows.test

import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.BELGIUM
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.JAPAN
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.IssueTeamWrapper
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.MatchDayFlow
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.generateQuickWinner
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.service.TournamentService
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.teams
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MatchDayFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var charlieNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = MockNetwork(
                parameters = MockNetworkParameters(
                        cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES,
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
                )
        )
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `match day outcome across multiple nodes`() {
        val aliceAccountService = aliceNode.services.accountService
        val testAccountA = aliceAccountService.createAccount("TEST_ACCOUNT_A").getOrThrow()
        val teamA = aliceNode.startFlow(IssueTeamWrapper(testAccountA, WorldCupTeam(JAPAN, true))).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        val bobAccountService = bobNode.services.accountService
        val testAccountB = bobAccountService.createAccount("TEST_ACCOUNT_B").getOrThrow()
        val teamB = bobNode.startFlow(IssueTeamWrapper(testAccountB, WorldCupTeam(BELGIUM, true))).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        aliceAccountService.shareAccountInfoWithParty(testAccountA.state.data.identifier.id, charlieNode.info.legalIdentities.first()).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        bobAccountService.shareAccountInfoWithParty(testAccountB.state.data.identifier.id, charlieNode.info.legalIdentities.first()).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        aliceNode.startFlow(ShareStateAndSyncAccounts(teamA, charlie)).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        bobNode.startFlow(ShareStateAndSyncAccounts(teamB, charlie)).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }


        val matchResult = charlieNode.startFlow(MatchDayFlow(teamB, teamA, teamB)).run {
            mockNet.runNetwork()
            getOrThrow()
        }

        val charlieAccountService = charlieNode.services.accountService

        val accountOfWinner = charlieNode.transaction {
            charlieAccountService.accountInfo(matchResult.state.data.owningKey!!)
        }

        Assert.assertThat(accountOfWinner!!.state.data.identifier, `is`(testAccountB.state.data.identifier))
    }


    @Test
    fun `run multiple match day flows`() {
        // Note from Roger on 30/09/2019.
        // Not sure what Charlie node does here. None of the states are relevant for it as the keys are generated on the
        // Alice node, therefore the test would always fail. Not sure how the change from Corda 4.1 to 4.3 effected this
        // but the current behaviour is expected, in that the Team states are relevant to ALICE but not CHARLIE, therefore
        // CHARLIE can't actually do anything... As such, I've changed the test so that all the flows are run from ALICE.
        val accountOwningService = aliceNode.services.accountService
        val tournamentServiceA = aliceNode.services.cordaService(TournamentService::class.java)
        createAccountsForNode(accountOwningService)
        val accounts = accountOwningService.allAccounts()

        accounts.forEach {
            aliceNode.startFlow(ShareStateAndSyncAccounts(it, charlie)).also {
                mockNet.runNetwork()
                it.getOrThrow()
            }
        }

        accounts.zip(teams).forEach {
            charlieNode.startFlow(IssueTeamWrapper(it.first, it.second)).also {
                mockNet.runNetwork()
                it.getOrThrow()
            }
        }

        val teams = aliceNode.transaction {
            tournamentServiceA.getTeamStates()
        }

        for (i in 1..teams.size step 2) {
            val teamA = teams[i - 1]
            val teamB = teams[i]

            aliceNode.startFlow(MatchDayFlow(generateQuickWinner(teamA, teamB), teamA, teamB)).run {
                mockNet.runNetwork()
                getOrThrow()
            }
        }

        val winningTeams = aliceNode.transaction {
            tournamentServiceA.getWinningTeamStates()
        }

        Assert.assertThat(winningTeams.size, `is`(4))

        for (i in 1..winningTeams.size step 2) {
            val teamA = winningTeams[i - 1]
            val teamB = winningTeams[i]

            aliceNode.startFlow(MatchDayFlow(generateQuickWinner(teamA, teamB), teamA, teamB)).run {
                mockNet.runNetwork()
                getOrThrow()
            }
        }

        val result = tournamentServiceA.getWinningTeamStates()
        Assert.assertThat(result.size, `is`(2))
    }
}