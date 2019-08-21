package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountObservedQueryBy
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
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

    fun StartedMockNode.identity() = info.legalIdentities.first()

    fun StartedMockNode.shareAccountInfo(accountInfo: StateAndRef<AccountInfo>, recipient: StartedMockNode): CordaFuture<Unit> {
        return startFlow(ShareAccountInfo(accountInfo, listOf(recipient.identity())))
    }

    @Test
    fun `should share state with only specified account`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        // Create accounts on node A.
        val futureA1 = a.startFlow(CreateAccount("A_Account1")).toCompletableFuture()
        val futureA2 = a.startFlow(CreateAccount("A_Account2")).toCompletableFuture()
        val futureA3 = a.startFlow(CreateAccount("A_Account3")).toCompletableFuture()

        // Create accounts on node B.
        val futureB1 = b.startFlow(CreateAccount("B_Account1")).toCompletableFuture()
        val futureB2 = b.startFlow(CreateAccount("B_Account2")).toCompletableFuture()
        val futureB3 = b.startFlow(CreateAccount("B_Account3")).toCompletableFuture()

        network.runNetwork()

        CompletableFuture.allOf(futureA1, futureA2, futureA3, futureB1, futureB2, futureB3)

        val a1 = futureA1.getOrThrow()
        val a2 = futureA2.getOrThrow()
        val a3 = futureA3.getOrThrow()
        val b1 = futureB1.getOrThrow()
        val b2 = futureB2.getOrThrow()
        val b3 = futureB3.getOrThrow()

        // Share all accounts on B with node A.
        val shareB1ToAFuture = b.shareAccountInfo(b1, a).toCompletableFuture()
        val shareB2ToAFuture = b.shareAccountInfo(b2, a).toCompletableFuture()
        val shareB3ToAFuture = b.shareAccountInfo(b3, a).toCompletableFuture()

        network.runNetwork()

        CompletableFuture.allOf(shareB1ToAFuture, shareB2ToAFuture, shareB3ToAFuture).getOrThrow()

        // Share account A1 with node B1, etc.

        //share accountA1 with ONLY accountB1 rather than the entire B node
        val resultOfPermissionedShareA1B1 = accountServiceOnA.shareStateWithAccount(b1.uuid, a1)
        //share accountA2 with ONLY accountB2 rather than the entire B node
        val resultOfPermissionedShareA2B2 = accountServiceOnA.shareStateWithAccount(b2.uuid, a2)
        //share accountA3 with ONLY accountB3 rather than the entire B node
        val resultOfPermissionedShareA3B3 = accountServiceOnA.shareStateWithAccount(b3.uuid, a3)

        network.runNetwork()

        CompletableFuture.allOf(
                resultOfPermissionedShareA1B1.toCompletableFuture(),
                resultOfPermissionedShareA2B2.toCompletableFuture(),
                resultOfPermissionedShareA3B3.toCompletableFuture()
        ).getOrThrow()

        val permissionedStatesForAccountB1 = b.transaction {
            b.services.vaultService.accountObservedQueryBy<AccountInfo>(
                    listOf(b1.uuid),
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        val permissionedStatesForAccountB2 = b.transaction {
            b.services.vaultService.accountObservedQueryBy<AccountInfo>(
                    listOf(b2.uuid),
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        val permissionedStatesForAccountB3 = b.transaction {
            b.services.vaultService.accountObservedQueryBy<AccountInfo>(
                    listOf(b3.uuid),
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        //AccountB1 should be permissioned to look at AccountA1
        Assert.assertThat(permissionedStatesForAccountB1, `is`(IsEqual.equalTo(listOf(a1.ref))))
        //AccountB2 should be permissioned to look at AccountA2
        Assert.assertThat(permissionedStatesForAccountB2, `is`(IsEqual.equalTo(listOf(a2.ref))))
        //AccountB3 should be permissioned to look at AccountA3
        Assert.assertThat(permissionedStatesForAccountB3, `is`(IsEqual.equalTo(listOf(a3.ref))))

        //share accountA1 with ONLY accountB3 rather than the entire B node
        val resultOfPermissionedShareA1B3 = accountServiceOnA.shareStateWithAccount(b3.uuid, a1)
        network.runNetwork()
        resultOfPermissionedShareA1B3.getOrThrow()

        val permissionedStatesForAccountB3AfterA1Shared = b.transaction {
            b.services.vaultService.accountObservedQueryBy<AccountInfo>(
                    listOf(b3.uuid),
                    QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        Assert.assertThat(permissionedStatesForAccountB3AfterA1Shared, (containsInAnyOrder(a3.ref, a1.ref)))
    }
}