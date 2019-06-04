package net.corda.accounts.cordapp.sweepstake.flows

import com.r3.corda.sdk.token.money.GBP
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.flows.ReceiveStateAndSyncAccountsFlow
import net.corda.accounts.flows.ShareStateAndSyncAccountsFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.Before
import org.junit.Test

class DistributeWinningsTest {

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
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        bobNode.registerInitiatedFlow(IssueTeamResponse::class.java)
    }

    @Test
    fun `distribute winnings between groups`() {

        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        createAccountsForNode(aliceService)

        val accounts = aliceService.myAccounts()

        aliceService.services.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, 8, bobNode.info.singleIdentity())

        // Issue the teams
        accounts.zip(TestUtils.teams).forEach {
            bobNode.services.startFlow(IssueTeamWrapper(it.first, it.second)).also {
                mockNet.runNetwork()
                it.resultFuture.getOrThrow()
            }
        }

        val tournamentService = aliceNode.services.cordaService(TournamentService::class.java)

        // Share all of the state data with charlie
        accounts.forEach {
            aliceService.shareAccountInfoWithParty(it.state.data.accountId, charlieNode.info.legalIdentities.first()).also {
                mockNet.runNetwork()
                it.getOrThrow()
            }
        }


        aliceNode.services.vaultService.queryBy<AccountGroup>().states.forEach {
            aliceNode.services.startFlow(ShareStateAndSyncAccountsFlow(it, charlie)).also {
                mockNet.runNetwork()
                it.resultFuture.getOrThrow()
            }
        }

        tournamentService.getTeamStates().forEach {
            aliceNode.services.startFlow(ShareStateAndSyncAccountsFlow(it, charlie)).also {
                mockNet.runNetwork()
                it.resultFuture.getOrThrow()
            }
        }

        // Mock up two winning teams
        val winners = tournamentService.getTeamStates().take(2)

        // TODO I DONT WORK HA HA HA HA
        charlieNode.services.startFlow(DistributeWinningsFlow(winners, 200L, GBP)).also {
            mockNet.runNetwork()
            it.resultFuture.getOrThrow()
        }
    }
}