package net.corda.accounts.flows.test

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ReceiveStateForAccountFlow
import net.corda.accounts.flows.ShareAccountInfoWithNodes
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture

class AccountsFlowTests {

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
    fun `should create new account`() {
        val future = a.startFlow(OpenNewAccountFlow("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        Assert.assertTrue(storedAccountInfo == result)
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


    @Test
    fun `should share state with only specified account`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnB = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        val futureA1 = a.startFlow(OpenNewAccountFlow("A_Account1"))
        val futureA2 = a.startFlow(OpenNewAccountFlow("A_Account2"))
        val futureA3 = a.startFlow(OpenNewAccountFlow("A_Account3"))

        val futureB1 = b.startFlow(OpenNewAccountFlow("B_Account1"))
        val futureB2 = b.startFlow(OpenNewAccountFlow("B_Account2"))
        val futureB3 = b.startFlow(OpenNewAccountFlow("B_Account3"))

        network.runNetwork()

        val shareB1ToAFuture = b.startFlow(ShareAccountInfoWithNodes(futureB1.getOrThrow(), listOf(a.info.legalIdentities.first()))).toCompletableFuture()
        val shareB2ToAFuture = b.startFlow(ShareAccountInfoWithNodes(futureB2.getOrThrow(), listOf(a.info.legalIdentities.first()))).toCompletableFuture()
        val shareB3ToAFuture = b.startFlow(ShareAccountInfoWithNodes(futureB3.getOrThrow(), listOf(a.info.legalIdentities.first()))).toCompletableFuture()
        network.runNetwork()

        CompletableFuture.allOf(shareB1ToAFuture, shareB2ToAFuture, shareB3ToAFuture).getOrThrow()

        //share accountA1 with ONLY accountB1 rather than the entire B node
        val resultOfPermissionedShareA1B1 = accountServiceOnA.broadcastStateToAccount(futureB1.getOrThrow().state.data.accountId, futureA1.getOrThrow())
        //share accountA2 with ONLY accountB2 rather than the entire B node
        val resultOfPermissionedShareA2B2 = accountServiceOnA.broadcastStateToAccount(futureB2.getOrThrow().state.data.accountId, futureA2.getOrThrow())
        //share accountA3 with ONLY accountB3 rather than the entire B node
        val resultOfPermissionedShareA3B3 = accountServiceOnA.broadcastStateToAccount(futureB3.getOrThrow().state.data.accountId, futureA3.getOrThrow())
        network.runNetwork()
        CompletableFuture.allOf(resultOfPermissionedShareA1B1.toCompletableFuture(), resultOfPermissionedShareA2B2.toCompletableFuture(), resultOfPermissionedShareA3B3.toCompletableFuture()).getOrThrow()


        val permissionedStatesForAccountB1 = b.transaction {
            accountServiceOnB.broadcastedToAccountVaultQuery(
                    futureB1.getOrThrow().state.data.accountId,
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            )
        }.map { it.ref }

        val permissionedStatesForAccountB2 = b.transaction {
            accountServiceOnB.broadcastedToAccountVaultQuery(
                    futureB2.getOrThrow().state.data.accountId,
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            )
        }.map { it.ref }

        val permissionedStatesForAccountB3 = b.transaction {
            accountServiceOnB.broadcastedToAccountVaultQuery(
                    futureB3.getOrThrow().state.data.accountId,
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            )
        }.map { it.ref }

        //AccountB1 should be permissioned to look at AccountA1
        Assert.assertThat(permissionedStatesForAccountB1, `is`(IsEqual.equalTo(listOf(futureA1.getOrThrow().ref))))
        //AccountB2 should be permissioned to look at AccountA2
        Assert.assertThat(permissionedStatesForAccountB2, `is`(IsEqual.equalTo(listOf(futureA2.getOrThrow().ref))))
        //AccountB3 should be permissioned to look at AccountA3
        Assert.assertThat(permissionedStatesForAccountB3, `is`(IsEqual.equalTo(listOf(futureA3.getOrThrow().ref))))

        //share accountA1 with ONLY accountB3 rather than the entire B node
        val resultOfPermissionedShareA1B3 = accountServiceOnA.broadcastStateToAccount(futureB3.getOrThrow().state.data.accountId, futureA1.getOrThrow())
        network.runNetwork()
        resultOfPermissionedShareA1B3.getOrThrow()

        val permissionedStatesForAccountB3AfterA1Shared = b.transaction {
            accountServiceOnB.broadcastedToAccountVaultQuery(
                    futureB3.getOrThrow().state.data.accountId,
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            )
        }.map { it.ref }

        Assert.assertThat(permissionedStatesForAccountB3AfterA1Shared, (IsIterableContainingInAnyOrder.containsInAnyOrder(futureA3.getOrThrow().ref, futureA1.getOrThrow().ref)))

    }
}