package net.corda.accounts.flows.test

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ReceiveStateForAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ShareAccountTests {

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
    fun `should send account info to party provided`() {
        val future = a.startFlow(OpenNewAccountFlow("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccount = a.transaction {
            val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
            Assert.assertTrue(storedAccountInfo == result)
            storedAccountInfo
        }

        val aAccountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        a.transaction {
            val foundAccount = aAccountService.accountInfo(result.state.data.accountId)
            Assert.assertThat(foundAccount, `is`(storedAccount))
        }

        aAccountService.shareAccountInfoWithParty(result.state.data.accountId, b.info.legalIdentities.first()).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val bAccountService = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        val accountOnB = b.transaction {
            bAccountService.accountInfo(result.state.data.accountId)
        }
        Assert.assertThat(accountOnB, `is`(storedAccount))
    }

}