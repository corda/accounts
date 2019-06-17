package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AccountGroupingFlowTests {

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
                        cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES_TESTCORDAPP,
                        threadPerNode = true
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
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `assign accounts to groups`() {
        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        createAccountsForNode(aliceService)

        val accounts = aliceService.ourAccounts()

        aliceService.services.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, 8)

        val aliceGroupStates = aliceNode.services.vaultService.queryBy<AccountGroup>().states

        Assert.assertThat(aliceGroupStates.size, CoreMatchers.`is`(IsEqual.equalTo(accounts.size / 4)))

        // There should be 4 accounts in each group
        aliceGroupStates.forEach {
            Assert.assertThat(it.state.data.accounts.size, CoreMatchers.`is`(IsEqual.equalTo(4)))
        }

        val group1 = aliceGroupStates[0].state.data
        val group2 = aliceGroupStates[1].state.data

        // Verify no account is assigned to both groups
        Assert.assertThat(group1.accounts.toMutableList().retainAll(group2.accounts.toMutableList()), CoreMatchers.`is`(IsEqual.equalTo(true)))
    }
}