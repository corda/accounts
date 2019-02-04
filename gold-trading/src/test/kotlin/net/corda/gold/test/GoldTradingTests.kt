package net.corda.gold.test

import net.corda.accounts.flows.GetAccountInfo
import net.corda.accounts.flows.ShareAccountInfoWithNodes
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.gold.trading.GoldBrick
import net.corda.gold.trading.MineBrickFlow
import net.corda.gold.trading.MoveGoldBrickToAccountFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class GoldTradingTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            listOf("net.corda.gold", "net.corda.accounts.service"), MockNetworkParameters(
                networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4
                )
            )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()

        a.registerInitiatedFlow(GetAccountInfo::class.java)
        b.registerInitiatedFlow(GetAccountInfo::class.java)
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should mine new gold brick`() {
        val future = a.startFlow(MineBrickFlow())
        network.runNetwork()
        val result = future.getOrThrow()
        Assert.assertThat(result.state.data, `is`(notNullValue(GoldBrick::class.java)))
    }

    @Test
    fun `should transfer gold to account on same node`() {

        val createdAccountFuture =
            a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccount = createdAccountFuture.getOrThrow()

        val miningFuture = a.startFlow(MineBrickFlow())
        network.runNetwork()
        val miningResult = miningFuture.getOrThrow()


        val moveFuture = a.startFlow(MoveGoldBrickToAccountFlow(createdAccount.state.data.accountId, miningResult))
        network.runNetwork()
        moveFuture.getOrThrow()
    }

    @Test
    fun `should transfer gold to account on different node`() {
        //MINE ON B
        val miningFuture = b.startFlow(MineBrickFlow())
        network.runNetwork()
        val minedGoldBrickOnB = miningFuture.getOrThrow()

        //CREATE ACCOUNT ON A
        val createdAccountFuture =
            a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccountOnA = createdAccountFuture.getOrThrow()

        //SHARE ACCOUNT FROM A -> B
        val shareFuture =
            a.startFlow(ShareAccountInfoWithNodes(createdAccountOnA, listOf(b.info.legalIdentities.first())))
        network.runNetwork()
        val sharedSuccesfully = shareFuture.getOrThrow()

        Assert.assertTrue(sharedSuccesfully)

        //CHECK THAT A AND B HAVE SAME VIEW OF THE ACCOUNT
        val bViewOfAccount = b.transaction {
            b.services.cordaService(KeyManagementBackedAccountService::class.java)
                .accountInfo(createdAccountOnA.state.data.accountId)
        }

        Assert.assertThat(bViewOfAccount, `is`(equalTo(createdAccountOnA)))

        //ATTEMPT TO MOVE FRESHLY MINED GOLD BRICK ON B TO AN ACCOUNT ON A
        val moveFuture =
            b.startFlow(MoveGoldBrickToAccountFlow(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        val resultOfMoveOnB = moveFuture.getOrThrow()!!
        println(resultOfMoveOnB)

        val goldBrickOnA = a.transaction {
            a.services.vaultService.queryBy(GoldBrick::class.java).states.single()
        }

        //CHECK THAT A AND B HAVE SAME VIEW OF MOVED BRICK
        Assert.assertThat(resultOfMoveOnB, `is`(equalTo(goldBrickOnA)))
    }
}