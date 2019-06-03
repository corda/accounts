package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.BELGIUM
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.JAPAN
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.teams
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.flows.ReceiveStateAndSyncAccountsFlow
import net.corda.accounts.flows.ShareStateAndSyncAccountsFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MatchDayFlowTests {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var charlieNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = InternalMockNetwork(
                cordappPackages = REQUIRED_CORDAPP_PACKAGES,
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        aliceNode.registerInitiatedFlow(MatchDayHandler::class.java)
        aliceNode.registerInitiatedFlow(ReceiveStateAndSyncAccountsFlow::class.java)
        bobNode.registerInitiatedFlow(MatchDayHandler::class.java)
        bobNode.registerInitiatedFlow(ReceiveStateAndSyncAccountsFlow::class.java)
        charlieNode.registerInitiatedFlow(MatchDayHandler::class.java)
        charlieNode.registerInitiatedFlow(ReceiveStateAndSyncAccountsFlow::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `match day outcome`() {
        val aliceAccountService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccountA = aliceAccountService.createAccount("TEST_ACCOUNT_A").getOrThrow()
        val teamA = aliceNode.services.startFlow(IssueTeamWrapper(testAccountA, WorldCupTeam(JAPAN, true))).let {
            mockNet.runNetwork()
            it.resultFuture.getOrThrow()
        }

        val bobAccountService = bobNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccountB = bobAccountService.createAccount("TEST_ACCOUNT_B").getOrThrow()
        val teamB = bobNode.services.startFlow(IssueTeamWrapper(testAccountB, WorldCupTeam(BELGIUM, true))).let {
            mockNet.runNetwork()
            it.resultFuture.getOrThrow()
        }

        aliceAccountService.shareAccountInfoWithParty(testAccountA.state.data.accountId, charlieNode.info.legalIdentities.first()).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        bobAccountService.shareAccountInfoWithParty(testAccountB.state.data.accountId, charlieNode.info.legalIdentities.first()).also {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        aliceNode.services.startFlow(ShareStateAndSyncAccountsFlow(teamA, charlie)).also {
            mockNet.runNetwork()
            it.resultFuture.getOrThrow()
        }

        bobNode.services.startFlow(ShareStateAndSyncAccountsFlow(teamB, charlie)).also {
            mockNet.runNetwork()
            it.resultFuture.getOrThrow()
        }


        val matchResult = charlieNode.services.startFlow(MatchDayFlow(generateQuickWinner(teamA, teamB), teamA, teamB)).resultFuture.run {
            mockNet.runNetwork()
            getOrThrow()
        }

        val charlieAccountService = charlieNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        val accountOfWinner = charlieNode.database.transaction {
            charlieAccountService.accountInfo(matchResult.state.data.owningKey!!)
        }
        Assert.assertThat(accountOfWinner!!.state.data.accountId, `is`(testAccountB.state.data.accountId))
    }


    @Test
    fun `run multiple match day flows`() {
        val accountOwningService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        createAccountsForNode(accountOwningService)
        val accounts = accountOwningService.allAccounts()

        // Alice creates accounts and shares them with charlie
        accounts.forEach {
            accountOwningService.shareAccountInfoWithParty(it.state.data.accountId, charlieNode.info.legalIdentities.first()).also {
                mockNet.runNetwork()
                it.getOrThrow()
            }
        }

        // Bob issues the teams
        accounts.zip(teams).forEach {
            bobNode.services.startFlow(IssueTeamWrapper(it.first, it.second)).also {
                mockNet.runNetwork()
                it.resultFuture.getOrThrow()
            }
        }

        val tournamentService = aliceNode.services.cordaService(TournamentService::class.java)
        val teams = tournamentService.getTeamStates()

        // Share the team states with charlie so he can run the match day flows
        teams.forEach {
            aliceNode.services.startFlow(ShareStateAndSyncAccountsFlow(it, charlie)).also {
                mockNet.runNetwork()
                it.resultFuture.getOrThrow()
            }
        }

        for (i in 1..teams.size step 2) {
            val teamA = teams[i - 1]
            val teamB = teams[i]

            charlieNode.services.startFlow(MatchDayFlow(generateQuickWinner(teamA, teamB), teamA, teamB)).resultFuture.run {
                mockNet.runNetwork()
                getOrThrow()
            }
        }

        val winningTeams = tournamentService.getWinningTeamStates()
        Assert.assertThat(winningTeams.size, `is`(4))

        for (i in 1..winningTeams.size step 2) {
            val teamA = winningTeams[i - 1]
            val teamB = winningTeams[i]

            charlieNode.services.startFlow(MatchDayFlow(generateQuickWinner(teamA, teamB), teamA, teamB)).resultFuture.run {
                mockNet.runNetwork()
                getOrThrow()
            }
        }

        val result = tournamentService.getWinningTeamStates()
        Assert.assertThat(result.size, `is`(2))
    }
}