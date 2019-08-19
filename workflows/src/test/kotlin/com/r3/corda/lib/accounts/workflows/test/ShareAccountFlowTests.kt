package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ShareAccountTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode


    @Before
    fun setup() {
        network = MockNetwork(
            cordappPackages = listOf("com.r3.corda.lib.accounts.contracts", "com.r3.corda.lib.accounts.workflows"),
            defaultParameters = MockNetworkParameters(networkParameters = testNetworkParameters(minimumPlatformVersion = 4))
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()
        nodeC = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    //possible to share the shared account with different host
    @Test
    fun `should be possible to share the shared account`() {
        //host node A will create an coount
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        // host node A will share the created account with node B
        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        val shareAccountInfoB=nodeB.services.vaultService.queryBy(AccountInfo::class.java).states.single()

        //node B will share the shared account with node C
        nodeB.startFlow(ShareAccountInfo(shareAccountInfoB, listOf(nodeC.identity()))).runAndGet(network)

        val shareAccountInfoC=nodeC.services.vaultService.queryBy(AccountInfo::class.java).states.single()

        //check whether the account in node A and node C is same.
        assertEquals(accountA,shareAccountInfoC)

    }


    //shared account will be available in both
    @Test
    fun `should shared account be available with the both`()
    {
        //host node A will create an account
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        // host node A will share the created account with node B
        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        val shareAccountInfoA=nodeA.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        val shareAccountInfoB=nodeB.services.vaultService.queryBy(AccountInfo::class.java).states.single()

        //check whether the account in node A and node B is same.
        assertEquals(shareAccountInfoA,shareAccountInfoB)

    }

    /*This test case checks whether a node get only its hosted and shared accounts*/
    @Test
    fun `should get only the hosted and shared accounts`(){

        //creating accounts on node A
        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val accountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)
        //creating an account on node B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //sharing accountA1 with node B
        nodeA.startFlow(ShareAccountInfo(accountA1, listOf(nodeB.identity()))).runAndGet(network)

        //Host accounts of node B
        val hostedAccountOfB=nodeB.startFlow(OurAccounts()).runAndGet(network)

        //all account states of node B
        val accountsOfB = nodeB.services.vaultService.queryBy(AccountInfo::class.java).states

        //checking whether accountB is present in node B's hosted accounts
        Assert.assertEquals(accountB,hostedAccountOfB[0])
        //checking whether accountA1 and accountB is present in node B's account states
        Assert.assertThat(accountsOfB, containsInAnyOrder(accountA1,accountB))
        //checking whether accountA2 is not present in node B's account states
        Assert.assertThat(accountsOfB,not(hasItem(accountA2)))

    }

}