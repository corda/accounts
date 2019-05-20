package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.BELGIUM
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.JAPAN
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture

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
        val teamA = aliceNode.services.startFlow(IssueTeamWrapper(testAccountA, WorldCupTeam(JAPAN))).resultFuture.getOrThrow()

        //Share alice's account with charlie
        aliceAccountService.shareAccountInfoWithParty(testAccountA.state.data.accountId, charlieNode.info.legalIdentities.first()).getOrThrow()

        val bobAccountService = bobNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        val testAccountB = bobAccountService.createAccount("TEST_ACCOUNT_B").getOrThrow()
        val teamB = aliceNode.services.startFlow(IssueTeamWrapper(testAccountB, WorldCupTeam(BELGIUM))).resultFuture.getOrThrow()

        //Share bob's accounts with charlie
        bobAccountService.shareAccountInfoWithParty(testAccountB.state.data.accountId, charlieNode.info.legalIdentities.first()).getOrThrow()

        val matchResult = charlieNode.services.startFlow(MatchDayWrapper(bob, teamA, teamB)).resultFuture.getOrThrow()

    }

    @Test
    fun `what`() {
        println(random63BitValue().toChar())
        println(random63BitValue())
        println(random63BitValue())
        println(random63BitValue())
        println(random63BitValue())
        println(random63BitValue())
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