package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.BELGIUM
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.JAPAN
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.REQUIRED_CORDAPP_PACKAGES
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.hamcrest.CoreMatchers
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
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = true,
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        aliceNode.registerInitiatedFlow(MatchDayWrapperHandler::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `match day outcome`(){
        val aliceAccountService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccountA = aliceAccountService.createAccount("TEST_ACCOUNT_A").getOrThrow()
        val teamA = aliceNode.services.startFlow(IssueTeamWrapper(testAccountA, WorldCupTeam(JAPAN, true))).resultFuture.getOrThrow()

        val bobAccountService = bobNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccountB = bobAccountService.createAccount("TEST_ACCOUNT_B").getOrThrow()
        val teamB = bobNode.services.startFlow(IssueTeamWrapper(testAccountB, WorldCupTeam(BELGIUM, true))).resultFuture.getOrThrow()

        //Share alice's account with charlie
        aliceAccountService.shareAccountInfoWithParty(testAccountA.state.data.accountId, charlieNode.info.legalIdentities.first()).getOrThrow()
        //Share bob's accounts with charlie
        bobAccountService.shareAccountInfoWithParty(testAccountB.state.data.accountId, charlieNode.info.legalIdentities.first()).getOrThrow()
        val charlieAccountService = charlieNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        val matchResult = charlieNode.services.startFlow(MatchDayWrapper(bob, teamA, teamB)).resultFuture.getOrThrow()

        val result = charlieNode.database.transaction {
            charlieNode.services.vaultService.queryBy(TeamState::class.java).states
        }

        println(result.size)
//        Assert.assertThat(result.size, `is`(equalTo(1)))

    }
}

@InitiatingFlow
private class MatchDayWrapper(private val otherParty: Party,
                              private val teamA: StateAndRef<TeamState>,
                              private val teamB: StateAndRef<TeamState>) : FlowLogic<StateAndRef<TeamState>>() {
    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        return(subFlow(MatchDayFlow(initiateFlow(otherParty), teamA, teamB)))
    }
}

@InitiatedBy(MatchDayWrapper::class)
private class MatchDayWrapperHandler(private val otherSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(MatchDayHandler(otherSession))
    }
}