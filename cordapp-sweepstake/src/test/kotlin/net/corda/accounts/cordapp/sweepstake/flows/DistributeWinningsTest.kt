package net.corda.accounts.cordapp.sweepstake.flows
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DistributeWinningsTest {

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP,
                        threadPerNode = true
                        )
                )

        aliceNode = mockNet.createPartyNode()
        bobNode = mockNet.createPartyNode()
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }


    @Test
    fun `distribute winnings between groups`() {

        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        createAccountsForNode(aliceService)

        val accounts = aliceService.ourAccounts()

        // Issue the teams
        accounts.zip(TestUtils.teams).forEach {
            bobNode.startFlow(IssueTeamWrapper(it.first, it.second))
        }

        aliceService.services.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, 8)

        val tournamentService = aliceNode.services.cordaService(TournamentService::class.java)

        // Mock up two winning teams
        val winners = tournamentService.getTeamStates().take(2)

        val winningParties = aliceNode.startFlow(DistributeWinningsFlow(winners, 200L, GBP)).toCompletableFuture().getOrThrow()

        val issuerCriteria = tokenAmountWithIssuerCriteria(GBP, aliceNode.services.myInfo.legalIdentities.first())
        val tokens = aliceNode.services.vaultService.queryBy<FungibleToken<*>>(issuerCriteria).states
        Assert.assertThat(tokens.size, `is`(IsEqual.equalTo(winningParties.size)))

        val winningQuantity = 200L/(winningParties.size) * 100
        tokens.forEach {
                Assert.assertThat(it.state.data.amount.quantity, `is`(IsEqual.equalTo(winningQuantity)))
        }
    }
}