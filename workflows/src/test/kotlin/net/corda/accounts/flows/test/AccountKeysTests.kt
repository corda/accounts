package net.corda.accounts.flows.test

import net.corda.accounts.workflows.flows.CreateAccount
import net.corda.accounts.workflows.flows.ReceiveStateForAccountFlow
import net.corda.accounts.workflows.flows.RequestKeyForAccountFlow
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AccountKeysTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                listOf(
                        "net.corda.accounts.contracts",
                        "net.corda.accounts.workflows"
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
    fun `should create multiple keys for an account when requested`() {

        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).let {
            network.runNetwork()
            it.getOrThrow()
        }


        val keyToUse1 = b.startFlow(RequestKeyForAccountFlow(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse2 = b.startFlow(RequestKeyForAccountFlow(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse3 = b.startFlow(RequestKeyForAccountFlow(account2.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        val foundKeysForAccount1 = a.transaction {
            accountService.accountKeys(account1.state.data.id)
        }

        val foundKeysForAccount2 = a.transaction {
            accountService.accountKeys(account2.state.data.id)
        }

        Assert.assertThat(foundKeysForAccount1, containsInAnyOrder(keyToUse1.owningKey, keyToUse2.owningKey))
        Assert.assertThat(foundKeysForAccount2, containsInAnyOrder(keyToUse3.owningKey))

    }

    @Test
    fun `should be possible to lookup account by previously used key`() {

        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse1 = b.startFlow(RequestKeyForAccountFlow(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse2 = b.startFlow(RequestKeyForAccountFlow(account2.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        a.transaction {
            Assert.assertThat(accountService.accountInfo(keyToUse1.owningKey), `is`(account1))
            Assert.assertThat(accountService.accountInfo(keyToUse2.owningKey), `is`(account2))
        }

    }

}