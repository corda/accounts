package com.r3.corda.lib.accounts.workflows.test

import com.natpryce.hamkrest.assertion.assertThat
import com.r3.corda.lib.accounts.contracts.states.AccountInfo

import com.r3.corda.lib.accounts.workflows.flows.AllAccounts
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.*
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.internal.matchers.Equals
import kotlin.reflect.jvm.internal.impl.util.ValueParameterCountCheck
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AllAccountTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

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
        a = network.createPartyNode()
        b = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    //this test will give the accounts which are present in a host

    @Test
    fun `should give all the accounts of a host`() {

        val account1 = a.startFlow(CreateAccount("TestAccountA1")).runAndGet(network)
        val account2 = a.startFlow(CreateAccount("TestAccountA2")).runAndGet(network)
        val allAccountInfoA = a.startFlow(AllAccounts()).runAndGet(network)
        //to check if the accounts are present in host
        Assert.assertEquals(listOf(account1, account2), allAccountInfoA)
    }

    //this test will give all hosted and shared account info
    @Test
    fun `should give all hosted and shared account info`() {

        val accountA = a.startFlow(CreateAccount("TestAccountA1")).runAndGet(network)
        val accountB = b.startFlow(CreateAccount("TestAccountB2")).runAndGet(network)

        a.startFlow(ShareAccountInfo(accountA, listOf(b.identity()))).runAndGet(network)

        val sharedAndHostAccountInfoB = b.services.vaultService.queryBy(AccountInfo::class.java).states

        val allAccountInfoA = a.startFlow(AllAccounts()).runAndGet(network)
        val allAccountInfoB = b.startFlow(AllAccounts()).runAndGet(network)

        Assert.assertEquals(sharedAndHostAccountInfoB, allAccountInfoB)
        //to check if the accounts are shared with host
        Assert.assertEquals(listOf(accountA), allAccountInfoA)
        Assert.assertEquals(listOf(accountB, accountA), allAccountInfoB)

    }

    //this test will not give account information  before creating account
    @Test
    fun `should not give account info before creating account`() {

        val allAccountInfoB = b.services.vaultService.queryBy(AccountInfo::class.java).states
        //to check if the account is not present even before creating account
        Assert.assertThat(allAccountInfoB.size ,`is`(0))

    }

    // this test will give shared account evenif it is not a host
    @Test
    fun `should give shared account even not a host`() {

        val account1 = a.startFlow(CreateAccount("TestAccountA1")).runAndGet(network)

        a.startFlow(ShareAccountInfo(account1, listOf(b.identity()))).runAndGet(network)

        val allAccountInfoB = b.startFlow(AllAccounts()).runAndGet(network)

        //to check the account even it is not a host
        Assert.assertEquals(account1, allAccountInfoB[0])

    }

}


