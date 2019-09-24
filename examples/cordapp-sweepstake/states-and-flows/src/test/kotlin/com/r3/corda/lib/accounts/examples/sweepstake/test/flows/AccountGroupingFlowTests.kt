package com.r3.corda.lib.accounts.examples.sweepstake.test.flows

import com.r3.corda.lib.accounts.examples.sweepstake.service.TournamentService
import com.r3.corda.lib.accounts.examples.sweepstake.states.AccountGroup
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
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
                parameters = MockNetworkParameters(
                        cordappsForAllNodes = TestUtils.REQUIRED_CORDAPP_PACKAGES,
                        networkSendManuallyPumped = false,
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
    fun `assign accounts to groups`() {
        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        createAccountsForNode(aliceService)

        val accounts = aliceService.ourAccounts()

        aliceService.services.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, 8, bobNode.info.singleIdentity())

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