package net.corda.gold.test

import net.corda.accounts.flows.GetAccountInfo
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.gold.trading.LoanBook
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
        Assert.assertThat(result.state.data, `is`(notNullValue(LoanBook::class.java)))
    }

    @Test
    fun `should transfer freshly gold to account on same node`() {
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
    fun `should transfer freshly mined gold to account on different node`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnB = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        //MINE ON B
        val miningFuture = b.startFlow(MineBrickFlow())
        network.runNetwork()
        val minedGoldBrickOnB = miningFuture.getOrThrow()

        //CREATE ACCOUNT ON A
        val createdAccountFuture = accountServiceOnA.createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccountOnA = createdAccountFuture.getOrThrow()

        //SHARE ACCOUNT FROM A -> B
        val shareFuture = accountServiceOnA.shareAccountInfoWithParty(createdAccountOnA.state.data.accountId, b.info.legalIdentities.first())
        network.runNetwork()
        val sharedSuccesfully = shareFuture.getOrThrow()

        Assert.assertTrue(sharedSuccesfully)

        //CHECK THAT A AND B HAVE SAME VIEW OF THE ACCOUNT
        val bViewOfAccount = b.transaction {
            accountServiceOnB.accountInfo(createdAccountOnA.state.data.accountId)
        }
        Assert.assertThat(bViewOfAccount, `is`(equalTo(createdAccountOnA)))

        //ATTEMPT TO MOVE FRESHLY MINED GOLD BRICK ON B TO AN ACCOUNT ON A
        val moveFuture = b.startFlow(MoveGoldBrickToAccountFlow(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        val resultOfMoveOnB = moveFuture.getOrThrow()
        println(resultOfMoveOnB)

        val goldBrickOnA = a.transaction {
            a.services.vaultService.queryBy(LoanBook::class.java).states.single()
        }
        //CHECK THAT A AND B HAVE SAME VIEW OF MOVED BRICK
        Assert.assertThat(resultOfMoveOnB, `is`(equalTo(goldBrickOnA)))
    }

    @Test
    fun `should transfer already owned gold to account on same node`() {

        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        // MINE ON B
        val miningFuture = b.startFlow(MineBrickFlow())
        network.runNetwork()
        val minedGoldBrickOnB = miningFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON A
        val createdAccountFuture = accountServiceOnA.createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccountOnA = createdAccountFuture.getOrThrow()

        //SHARE NEW ACCOUNT WITH B
        val shareFuture = accountServiceOnA.shareAccountInfoWithParty(createdAccountOnA.state.data.accountId, b.info.legalIdentities.first())
        network.runNetwork()
        shareFuture.getOrThrow()

        //ATTEMPT TO MOVE MINED BRICK TO ACCOUNT ON A
        val moveFuture = b.startFlow(MoveGoldBrickToAccountFlow(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        val resultOfMoveOnB = moveFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON A
        val newAccountOnAFuture = accountServiceOnA.createAccount("ANOTHER_TESTING_ACCOUNT")
        network.runNetwork()
        val newAccountOnA = newAccountOnAFuture.getOrThrow()

        //ATTEMPT TO MOVE ALREADY OWNED BRICK FROM ACCOUNT ON A TO ANOTHER ACCOUNT ON A
        val moveToNewAccountOnAFuture = a.startFlow(MoveGoldBrickToAccountFlow(newAccountOnA.state.data.accountId, resultOfMoveOnB))
        network.runNetwork()
        val movedToNewAccountBrick = moveToNewAccountOnAFuture.getOrThrow()

        Assert.assertThat(movedToNewAccountBrick.state.data.owningAccount, `is`(equalTo(newAccountOnA.state.data)))

    }

    @Test
    fun `should transfer already owned gold to account on different node`() {
        val c = network.createNode()
        c.registerInitiatedFlow(GetAccountInfo::class.java)

        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnC = c.services.cordaService(KeyManagementBackedAccountService::class.java)

        //MINE ON B
        val miningFuture = b.startFlow(MineBrickFlow())
        network.runNetwork()
        val minedGoldBrickOnB = miningFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON A
        val createdAccountFuture = accountServiceOnA.createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccountOnA = createdAccountFuture.getOrThrow()

        //SHARE NEW ACCOUNT WITH B
        val shareFuture = accountServiceOnA.shareAccountInfoWithParty(createdAccountOnA.state.data.accountId, b.info.legalIdentities.first())
        network.runNetwork()
        shareFuture.getOrThrow()

        //ATTEMPT TO MOVE MINED GOLD BRICK TO ACCOUNT ON A
        val moveFuture = b.startFlow(MoveGoldBrickToAccountFlow(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        //gold owned by account on A
        val resultOfMoveToAccountOnA = moveFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON NODE A
        val newAccountOnBFuture = accountServiceOnA.createAccount("ANOTHER_TESTING_ACCOUNT")
        network.runNetwork()
        val newAccountOnA = newAccountOnBFuture.getOrThrow()

        //ATTEMPT TO MOVE GOLD BRICK TO NEW ACCOUNT ON
        val moveToNewAccountOnAFuture = a.startFlow(MoveGoldBrickToAccountFlow(newAccountOnA.state.data.accountId, resultOfMoveToAccountOnA))
        network.runNetwork()
        //brick is now owned by another account on node A
        val movedToNewAccountOnABrick = moveToNewAccountOnAFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON NODE C
        val createAccountOnCFuture = accountServiceOnC.createAccount("ANOTHER_ANOTHER_TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccountOnC = createAccountOnCFuture.getOrThrow()

        //SHARE NEW ACCOUNT WITH NODE A (CURRENT OWNER)
        val shareNewAccountWithBFuture = accountServiceOnC.shareAccountInfoWithParty(createdAccountOnC.state.data.accountId, a.info.legalIdentities.first())
        network.runNetwork()
        shareNewAccountWithBFuture.getOrThrow()

        //ATTEMPT TO MOVE GOLD BRICK TO ACCOUNT ON NODE C
        val moveToAccountOnCFuture = a.startFlow(MoveGoldBrickToAccountFlow(createdAccountOnC.state.data.accountId, movedToNewAccountOnABrick))
        network.runNetwork()
        //gold now owned by account on C
        val resultOfMoveOnC = moveToAccountOnCFuture.getOrThrow()

        Assert.assertThat(resultOfMoveOnC.state.data.owningAccount?.accountHost, `is`(equalTo(c.info.legalIdentities.first())))
    }
}