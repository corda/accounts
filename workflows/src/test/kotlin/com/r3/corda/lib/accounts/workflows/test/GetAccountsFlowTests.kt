package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
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
import java.util.*
import kotlin.test.assertEquals

class GetAccountsFlowTests {

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
    fun `should lookup all hosted accounts`() {
        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).runAndGet(network)
        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).runAndGet(network)
        val account3 = a.startFlow(CreateAccount("Stefano_Account3")).runAndGet(network)
        val account4 = b.startFlow(CreateAccount("Stefano_Account3")).runAndGet(network)

        b.startFlow(ShareAccountInfo(account4, listOf(a.identity()))).runAndGet(network)

        val accountService = a.services.accountService

        a.transaction {
            Assert.assertThat(accountService.accountInfo(account4.uuid), `is`(account4))
            Assert.assertThat(accountService.ourAccounts(), containsInAnyOrder(account1, account2, account3))
            Assert.assertThat(accountService.ourAccounts(), not(hasItem(account4)))
        }

    }

    @Test
    fun `should lookup all accounts`() {
        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).runAndGet(network)
        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).runAndGet(network)
        val account3 = a.startFlow(CreateAccount("Stefano_Account3")).runAndGet(network)
        val account4 = b.startFlow(CreateAccount("Stefano_Account3")).runAndGet(network)

        b.startFlow(ShareAccountInfo(account4, listOf(a.identity()))).runAndGet(network)

        val accountService = a.services.accountService

        a.transaction {
            Assert.assertThat(accountService.allAccounts(), containsInAnyOrder(account1, account2, account3, account4))
        }

    }

    @Test
    fun `should be able to lookup account by UUID from service`() {
        val future = a.startFlow(CreateAccount("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccount = a.transaction {
            val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
            Assert.assertTrue(storedAccountInfo == result)
            storedAccountInfo
        }

        val accountService = a.services.accountService
        a.transaction {
            val foundAccount = accountService.accountInfo(result.uuid)
            Assert.assertThat(foundAccount, `is`(storedAccount))
        }
    }

    @Test
    fun `should be able to request account from other node, if it has it`() {
        val futureOne = a.startFlow(CreateAccount("Stefano_Account"))
        network.runNetwork()
        val result = futureOne.getOrThrow()

        // Successfully get back the requested account info.
        val accountId = result.state.data.identifier.id
        val futureTwo = b.startFlow(RequestAccountInfo(accountId, a.identity()))
        network.runNetwork()
        val resultTwo = futureTwo.getOrThrow()
        assertEquals(result.state.data, resultTwo)

        // B doesn't know this UUID, so return null.
        val futureThree = b.startFlow(RequestAccountInfo(UUID.randomUUID(), a.identity()))
        network.runNetwork()
        val resultThree = futureThree.getOrThrow()
        assertEquals(null, resultThree)
    }
}


