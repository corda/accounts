package net.corda.gold.test

import net.corda.core.utilities.getOrThrow
import net.corda.gold.trading.workflows.flows.GetAllWebUsersFlow
import net.corda.gold.trading.workflows.flows.NewWebAccountFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class WebPermissionTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode


    @Before
    fun setup() {
        network = MockNetwork(
            listOf("net.corda.gold.trading", "com.r3.corda.lib.accounts"),
            MockNetworkParameters(
                networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4
                )
            )
        )
        a = network.createPartyNode()

    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should create a web account`() {
        val createFuture = a.startFlow(NewWebAccountFlow("TestAccount")).toCompletableFuture()
        network.runNetwork()
        val createdAccount = (createFuture.getOrThrow())
        Assert.assertThat(
            a.startFlow(GetAllWebUsersFlow()).getOrThrow(),
            `is`(equalTo(listOf(createdAccount.webAccount)))
        )
    }
}


