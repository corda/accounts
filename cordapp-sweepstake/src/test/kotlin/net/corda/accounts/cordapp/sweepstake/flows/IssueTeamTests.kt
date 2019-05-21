package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.JAPAN
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class IssueTeamTests {

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
                cordappPackages = listOf(
                        "net.corda.accounts.cordapp.sweepstake.states",
                        "net.corda.accounts.cordapp.sweepstake.contracts",
                        "net.corda.accounts.cordapp.sweepstake.flows",
                        "net.corda.accounts.service",
                        "net.corda.accounts.contracts",
                        "net.corda.accounts.flows"),
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = true)

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
        val teams = generateTeamsFromFile("src/test/resources/worldcupteams.txt")
        assertThat(teams).hasSize(32)

        val players = generateParticipantsFromFile("src/test/resources/participants.txt")
        assertThat(players).hasSize(32)
    }

    @Test
    fun `issue a team to an account`() {
        val aliceAccountService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccount = aliceAccountService.createAccount("TEST_ACCOUNT").getOrThrow()
        val future = aliceNode.services.startFlow(IssueTeamWrapper(testAccount, WorldCupTeam(JAPAN, true))).resultFuture.getOrThrow()

        Assert.assertThat(future.state.data, `is`(notNullValue(TeamState::class.java)))
        Assert.assertThat(future.state.data.team.teamName, `is`(IsEqual.equalTo(JAPAN)))
        Assert.assertThat(future.state.data.owningAccountId, `is`(IsEqual.equalTo(testAccount.state.data.accountId)))
    }
}

@InitiatingFlow
internal class IssueTeamWrapper(private val accountInfo: StateAndRef<AccountInfo>,
                               private val team: WorldCupTeam): FlowLogic<StateAndRef<TeamState>>() {
    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        return(subFlow(IssueTeamFlow(accountInfo, team)))
    }
}


