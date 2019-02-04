package net.corda.accounts.flows.test

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.service.AccountInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OpenNewAccountFlowTest{

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            listOf("net.corda.accounts.model", "net.corda.accounts.service"), MockNetworkParameters(
                networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4
                )
            )
        )
        a = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should create new account`() {
        val future = a.startFlow(OpenNewAccountFlow("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        Assert.assertTrue(storedAccountInfo == result)
    }
}