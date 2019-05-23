package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class AccountGroupingFlowTests {

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
                cordappPackages = TestUtils.REQUIRED_CORDAPP_PACKAGES,
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

        bobNode.registerInitiatedFlow(IssueTeamResponse::class.java)
        bobNode.registerInitiatedFlow(IssueGroupResponse::class.java)
        bobNode.registerInitiatedFlow(UpdateGroupResponse::class.java)

    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `assign accounts to groups`() {
        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)
        createAccountsForNode(aliceService)

        val accounts = aliceService.myAccounts()


        val tournamentService = aliceService.services.cordaService(TournamentService::class.java)
        tournamentService.assignAccountstoGroups(accounts, 8, bob)


    }

    private fun createAccountsForNode(aliceService: KeyManagementBackedAccountService) {
        aliceService.createAccount("TEST_ACCOUNT_1").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_2").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_3").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_4").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_5").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_6").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_7").getOrThrow()
        aliceService.createAccount("TEST_ACCOUNT_8").getOrThrow()
    }
}