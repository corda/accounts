package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.JAPAN
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.workflows.flows.AllAccounts
import net.corda.accounts.workflows.flows.OurAccounts
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class IssueTeamFlowTests {

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
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP
                )
        )

        aliceNode = mockNet.createPartyNode()
        bobNode = mockNet.createPartyNode()
        charlieNode = mockNet.createPartyNode()
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        bobNode.registerInitiatedFlow(IssueTeamResponse::class.java)

    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `read data from input files`() {
        val teams = generateTeamsFromFile("src/test/resources/worldcupteams.txt")
        assertThat(teams).hasSize(32)

        val players = generateParticipantsFromFile("src/test/resources/participants.txt")
        assertThat(players).hasSize(32)
    }

    @Test
    fun `issue a team to an account`() {
        val aliceAccountService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccount = aliceAccountService.createAccount("TEST_ACCOUNT").getOrThrow()
        val future = aliceNode.startFlow(IssueTeamWrapper(testAccount, WorldCupTeam(JAPAN, true))).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }
        Assert.assertThat(future.state.data, `is`(notNullValue(TeamState::class.java)))
        Assert.assertThat(future.state.data.team.teamName, `is`(IsEqual.equalTo(JAPAN)))

        aliceNode.transaction {
            Assert.assertThat(testAccount, `is`(IsEqual.equalTo(aliceAccountService.accountInfo(future.state.data.owningKey!!))))
        }
    }

    @Test
    fun `issue a team to an account that is owned by a different node`() {
        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val aliceAccount = aliceService.createAccount("TEST_ACCOUNT").getOrThrow()

        //Share alice's account with bob
        aliceService.shareAccountInfoWithParty(aliceAccount.state.data.id, bobNode.info.singleIdentity())
        val future = bobNode.startFlow(IssueTeamWrapper(aliceAccount, WorldCupTeam(JAPAN, true))).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }

        val aliceAccounts = aliceNode.startFlow(OurAccounts()).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }
        val bobAccounts = bobNode.startFlow(AllAccounts()).let {
            mockNet.runNetwork()
            it.getOrThrow()
        }
        Assert.assertThat(bobAccounts, `is`(IsEqual.equalTo(aliceAccounts)))

        Assert.assertThat(future.state.data, `is`(notNullValue(TeamState::class.java)))
        Assert.assertThat(future.state.data.team.teamName, `is`(IsEqual.equalTo(JAPAN)))

        aliceNode.transaction {
            val owningAccount = aliceService.accountInfo(future.state.data.owningKey!!)
            Assert.assertThat(owningAccount!!.state.data.id, `is`(IsEqual.equalTo(aliceAccount.state.data.id)))
        }
    }
}