package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class OurAccountsTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should get all hosted accounts`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        val hostAccountInfoA = nodeA.startFlow(OurAccounts()).runAndGet(network)
        val hostAccountInfoB = nodeB.startFlow(OurAccounts()).runAndGet(network)

        Assert.assertEquals(accountA, hostAccountInfoA[0])
        Assert.assertEquals(accountB, hostAccountInfoB[0])
        Assert.assertNotEquals(accountB, hostAccountInfoA[0])
        Assert.assertNotEquals(accountA, hostAccountInfoB[0])

    }

    @Test
    fun `should not lookup shared accounts`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        val sharedAndHostAccountInfoB = nodeB.services.vaultService.queryBy(AccountInfo::class.java).states

        val hostAccountInfoA = nodeA.startFlow(OurAccounts()).runAndGet(network)
        val hostAccountInfoB = nodeB.startFlow(OurAccounts()).runAndGet(network)

        Assert.assertEquals(accountA, hostAccountInfoA[0])
        Assert.assertEquals(accountB, hostAccountInfoB[0])
        Assert.assertEquals(listOf(accountB,accountA), sharedAndHostAccountInfoB)
        Assert.assertNotEquals(hostAccountInfoB, sharedAndHostAccountInfoB)
    }


}


