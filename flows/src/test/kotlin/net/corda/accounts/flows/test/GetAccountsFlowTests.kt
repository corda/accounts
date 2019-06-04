package net.corda.accounts.flows.test

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ReceiveStateForAccountFlow
import net.corda.accounts.flows.ShareAccountWithParties
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.*
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class GetAccountsFlowTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                listOf(
                        "net.corda.accounts.model",
                        "net.corda.accounts.service",
                        "net.corda.accounts.contracts",
                        "net.corda.accounts.flows",
                        "net.corda.accounts.states"
                ), MockNetworkParameters(
                networkParameters = testNetworkParameters(
                        minimumPlatformVersion = 4
                )
        )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()

        a.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)
        b.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should lookup all hosted accounts`() {
        val account1 = a.startFlow(OpenNewAccountFlow("Stefano_Account1")).runAndGet(network)
        val account2 = a.startFlow(OpenNewAccountFlow("Stefano_Account2")).runAndGet(network)
        val account3 = a.startFlow(OpenNewAccountFlow("Stefano_Account3")).runAndGet(network)
        val account4 = b.startFlow(OpenNewAccountFlow("Stefano_Account3")).runAndGet(network)

        b.startFlow(ShareAccountWithParties(account4, listOf(a.identity()))).runAndGet(network)

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        a.transaction {
            Assert.assertThat(accountService.accountInfo(account4.state.data.accountId), `is`(account4))
            Assert.assertThat(accountService.myAccounts(), containsInAnyOrder(account1, account2, account3))
            Assert.assertThat(accountService.myAccounts(), not(hasItem(account4)))
        }

    }

    @Test
    fun `should lookup all accounts`() {
        val account1 = a.startFlow(OpenNewAccountFlow("Stefano_Account1")).runAndGet(network)
        val account2 = a.startFlow(OpenNewAccountFlow("Stefano_Account2")).runAndGet(network)
        val account3 = a.startFlow(OpenNewAccountFlow("Stefano_Account3")).runAndGet(network)
        val account4 = b.startFlow(OpenNewAccountFlow("Stefano_Account3")).runAndGet(network)

        b.startFlow(ShareAccountWithParties(account4, listOf(a.identity()))).runAndGet(network)

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        a.transaction {
            Assert.assertThat(accountService.allAccounts(), containsInAnyOrder(account1, account2, account3, account4))
        }

    }

    @Test
    fun `should be able to lookup account by UUID from service`() {
        val future = a.startFlow(OpenNewAccountFlow("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccount = a.transaction {
            val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
            Assert.assertTrue(storedAccountInfo == result)
            storedAccountInfo
        }

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        a.transaction {
            val foundAccount = accountService.accountInfo(result.state.data.accountId)
            Assert.assertThat(foundAccount, `is`(storedAccount))
        }
    }


}


