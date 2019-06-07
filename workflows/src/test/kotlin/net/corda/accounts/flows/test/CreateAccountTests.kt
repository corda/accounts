package net.corda.accounts.flows.test

import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.flows.CreateAccount
import net.corda.accounts.workflows.flows.ReceiveStateForAccountFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CreateAccountTests {

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
    fun `should create new account`() {
        val future = a.startFlow(CreateAccount("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        Assert.assertTrue(storedAccountInfo == result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should not be possible to create an account which has same name on a single host`() {
        val accountOne = a.startFlow(CreateAccount("Stefano_Account 1")).runAndGet(network)
        val accountTwo = a.startFlow(CreateAccount("Stefano_Account 1")).runAndGet(network)
    }

}