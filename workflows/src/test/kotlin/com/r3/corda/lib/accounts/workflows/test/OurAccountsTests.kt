package com.r3.corda.lib.accounts.workflows.test

import com.natpryce.hamkrest.assertion.assertThat
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
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

class OurAccountsTests {

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


    @Test
    fun `should get all hosted accounts`() {
        val accountA = a.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        val accountB = b.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        val hostAccountInfoA = a.startFlow(OurAccounts()).runAndGet(network)
        val hostAccountInfoB = b.startFlow(OurAccounts()).runAndGet(network)

        Assert.assertEquals(accountA, hostAccountInfoA[0])
        Assert.assertEquals(accountB, hostAccountInfoB[0])
        Assert.assertNotEquals(accountB, hostAccountInfoA[0])
        Assert.assertNotEquals(accountA, hostAccountInfoB[0])

    }

    @Test
    fun `should not lookup shared accounts`() {
        val accountA = a.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        val accountB = b.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        a.startFlow(ShareAccountInfo(accountA, listOf(b.identity()))).runAndGet(network)

        val sharedAndHostAccountInfoB = b.services.vaultService.queryBy(AccountInfo::class.java).states

        val hostAccountInfoA = a.startFlow(OurAccounts()).runAndGet(network)
        val hostAccountInfoB = b.startFlow(OurAccounts()).runAndGet(network)

        Assert.assertEquals(accountA, hostAccountInfoA[0])
        Assert.assertEquals(accountB, hostAccountInfoB[0])
        Assert.assertEquals(listOf(accountB,accountA), sharedAndHostAccountInfoB)
        Assert.assertNotEquals(hostAccountInfoB, sharedAndHostAccountInfoB)
    }


}


