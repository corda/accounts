package net.corda.accounts.flows.test

import net.corda.accounts.flows.*
import net.corda.accounts.states.AccountInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.collection.IsIterableContainingInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

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
                "net.corda.accounts.flows"
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
    fun `should share state with only specified account`() {

        val futureA = a.startFlow(OpenNewAccountFlow("A_Account"))
        val futureB = b.startFlow(OpenNewAccountFlow("B_Account"))

        network.runNetwork()
        val accountCreatedOnA = futureA.getOrThrow()
        val accountCreatedOnB = futureB.getOrThrow()

        val shareToAFuture = b.startFlow(ShareAccountInfoWithNodes(accountCreatedOnB, listOf(a.info.legalIdentities.first())))
        network.runNetwork()

        shareToAFuture.getOrThrow()

        //share accountA with ONLY accountB rather than the entire B node
        val resultOfPermissionedShare = a.startFlow(ShareStateWithAccountFlow(accountCreatedOnB.state.data, accountCreatedOnA))
        network.runNetwork()
        resultOfPermissionedShare.getOrThrow()

        val permissionedStatesForAccountB = b.transaction {
            b.services.withEntityManager {
                val itemFound = find(AllowedToSeeStateMapping::class.java, accountCreatedOnB.state.data.accountId).copy()
                println(itemFound)
                itemFound
            }
        }

        //AccountB should be permissioned to look at AccountA
        Assert.assertThat(permissionedStatesForAccountB.stateRef, IsIterableContainingInAnyOrder.containsInAnyOrder(accountCreatedOnA.ref))

    }
}