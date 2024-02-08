package com.r3.corda.lib.accounts.examples.sweepstake.workflows.test

import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.IssueTeamWrapper
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.generateParticipantsFromFile
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.flows.generateTeamsFromFile
import com.r3.corda.lib.accounts.examples.sweepstake.workflows.test.TestUtils.Companion.JAPAN
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.TeamState
import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import com.r3.corda.lib.accounts.workflows.flows.AllAccounts
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.core.IsEqual
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
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
                parameters = MockNetworkParameters(
                        cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES,
                        threadPerNode = true,
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
    fun `read data from input files`() {
        val teams = generateTeamsFromFile("src/main/resources/worldcupteams.txt")
        assertThat(teams).hasSize(32)

        val players = generateParticipantsFromFile("src/main/resources/participants.txt")
        assertThat(players).hasSize(32)
    }

    @Test
    fun `issue a team to an account that is owned by same node`() {
        val aliceService = aliceNode.services.accountService
        val aliceAccount = aliceService.createAccount("TEST_ACCOUNT").getOrThrow()

        //Share alice's account with bob
        aliceService.shareAccountInfoWithParty(aliceAccount.state.data.identifier.id, bobNode.info.singleIdentity())
        val teamState = aliceNode.startFlow(IssueTeamWrapper(aliceAccount, WorldCupTeam(JAPAN, true))).getOrThrow()

        assertThat(teamState.state.data, `is`(notNullValue(TeamState::class.java)))
        assertThat(teamState.state.data.team.teamName, `is`(IsEqual.equalTo(JAPAN)))

        aliceNode.transaction {
            val owningAccount = aliceService.accountInfo(teamState.state.data.owningKey!!)
            assertThat(owningAccount!!.state.data.identifier, `is`(IsEqual.equalTo(aliceAccount.state.data.identifier)))
        }
    }

    @Test
    fun `issue a team to an account that is owned by a different node`() {
        val aliceService = aliceNode.services.accountService
        val aliceAccount = aliceService.createAccount("TEST_ACCOUNT").getOrThrow()

        //Share alice's account with bob
        aliceService.shareAccountInfoWithParty(aliceAccount.state.data.identifier.id, bobNode.info.singleIdentity())
        val teamState = bobNode.startFlow(IssueTeamWrapper(aliceAccount, WorldCupTeam(JAPAN, true))).getOrThrow()

        val aliceAccounts = aliceNode.startFlow(OurAccounts()).getOrThrow()
        val bobAccounts = bobNode.startFlow(AllAccounts()).getOrThrow()
        assertThat(bobAccounts, `is`(IsEqual.equalTo(aliceAccounts)))

        assertThat(teamState.state.data, `is`(notNullValue(TeamState::class.java)))
        assertThat(teamState.state.data.team.teamName, `is`(IsEqual.equalTo(JAPAN)))

        aliceNode.transaction {
            val owningAccount = aliceService.accountInfo(teamState.state.data.owningKey!!)
            assertThat(owningAccount!!.state.data.identifier, `is`(IsEqual.equalTo(aliceAccount.state.data.identifier)))
        }
    }
}