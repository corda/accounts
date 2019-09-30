package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo

import com.r3.corda.lib.accounts.workflows.flows.AllAccounts

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AllAccountTests {

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

//this test will give the accounts which are present in nodeA host

    @Test
    fun `should give all the accounts of a host`() {

        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val accountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)
        val allAccountInfoA = nodeA.startFlow(AllAccounts()).runAndGet(network)
        //to check if the accounts are present in host
        Assert.assertEquals(listOf(accountA1, accountA2), allAccountInfoA)
    }

    //this test will give all hosted and shared account info
    @Test
    fun `should give all hosted and shared account info`() {

        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB2")).runAndGet(network)

        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        val sharedAndHostAccountInfoB = nodeB.services.vaultService.queryBy(AccountInfo::class.java).states

        val allAccountInfoA = nodeA.startFlow(AllAccounts()).runAndGet(network)
        val allAccountInfoB = nodeB.startFlow(AllAccounts()).runAndGet(network)

        Assert.assertEquals(sharedAndHostAccountInfoB, allAccountInfoB)
        //to check if the accounts are shared with host
        Assert.assertEquals(listOf(accountA), allAccountInfoA)
        Assert.assertEquals(listOf(accountB, accountA), allAccountInfoB)

    }

    //this test will not give account information  before creating account
    @Test
    fun `should not give account info before creating account`() {

        val allAccountInfoB = nodeB.services.vaultService.queryBy(AccountInfo::class.java).states
        //to check if the account is not present even before creating account
        Assert.assertThat(allAccountInfoB.size ,`is`(0))

    }

    // this test will give shared account evenif it is not nodeA host
    @Test
    fun `should give shared account even not a host`() {

        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        val allAccountInfoB = nodeB.startFlow(AllAccounts()).runAndGet(network)

        //to check the account even it is not nodeA host
        Assert.assertEquals(accountA, allAccountInfoB[0])

    }

}