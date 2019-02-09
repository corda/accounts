package net.corda.gold.test

import net.corda.accounts.flows.GetAccountInfo
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.sumByLong
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.gold.trading.IssueLoanBookFlow
import net.corda.gold.trading.LoanBook
import net.corda.gold.trading.MoveLoanBookToNewAccount
import net.corda.gold.trading.SplitLoanFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.hamcrest.CoreMatchers.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class LoanBookTradingTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            listOf("net.corda.gold", "net.corda.accounts.service", "net.corda.accounts.contracts"), MockNetworkParameters(
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
        val future = a.startFlow(IssueLoanBookFlow(100))
        network.runNetwork()
        val result = future.getOrThrow()
        Assert.assertThat(result.state.data, `is`(notNullValue(LoanBook::class.java)))
    }

    @Test
    fun `should transfer freshly created loanbook to account on same node`() {
        val createdAccountFuture =
            a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount("TESTING_ACCOUNT")
        network.runNetwork()
        val createdAccount = createdAccountFuture.getOrThrow()

        val miningFuture = a.startFlow(IssueLoanBookFlow(100))
        network.runNetwork()
        val miningResult = miningFuture.getOrThrow()


        val moveFuture = a.startFlow(MoveLoanBookToNewAccount(createdAccount.state.data.accountId, miningResult))
        network.runNetwork()
        moveFuture.getOrThrow()
    }

    @Test
    fun `should transfer freshly created loanbook to account on different node`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnB = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        //MINE ON B
        val miningFuture = b.startFlow(IssueLoanBookFlow(100))
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
        val moveFuture = b.startFlow(MoveLoanBookToNewAccount(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
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
    fun `should transfer already owned loanbook to account on same node`() {

        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        // MINE ON B
        val miningFuture = b.startFlow(IssueLoanBookFlow(100))
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
        val moveFuture = b.startFlow(MoveLoanBookToNewAccount(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        val resultOfMoveOnB = moveFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON A
        val newAccountOnAFuture = accountServiceOnA.createAccount("ANOTHER_TESTING_ACCOUNT")
        network.runNetwork()
        val newAccountOnA = newAccountOnAFuture.getOrThrow()

        //ATTEMPT TO MOVE ALREADY OWNED BRICK FROM ACCOUNT ON A TO ANOTHER ACCOUNT ON A
        val moveToNewAccountOnAFuture = a.startFlow(MoveLoanBookToNewAccount(newAccountOnA.state.data.accountId, resultOfMoveOnB))
        network.runNetwork()
        val movedToNewAccountBrick = moveToNewAccountOnAFuture.getOrThrow()

        Assert.assertThat(movedToNewAccountBrick.state.data.owningAccount, `is`(equalTo(newAccountOnA.state.data.signingKey)))
    }

    @Test
    fun `should transfer already owned loanbook to account on different node`() {
        val c = network.createNode()
        c.registerInitiatedFlow(GetAccountInfo::class.java)

        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnC = c.services.cordaService(KeyManagementBackedAccountService::class.java)

        //MINE ON B
        val miningFuture = b.startFlow(IssueLoanBookFlow(100))
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
        val moveFuture = b.startFlow(MoveLoanBookToNewAccount(createdAccountOnA.state.data.accountId, minedGoldBrickOnB))
        network.runNetwork()
        //gold owned by account on A
        val resultOfMoveToAccountOnA = moveFuture.getOrThrow()

        //CREATE NEW ACCOUNT ON NODE A
        val newAccountOnBFuture = accountServiceOnA.createAccount("ANOTHER_TESTING_ACCOUNT")
        network.runNetwork()
        val newAccountOnA = newAccountOnBFuture.getOrThrow()

        //ATTEMPT TO MOVE GOLD BRICK TO NEW ACCOUNT ON
        val moveToNewAccountOnAFuture = a.startFlow(MoveLoanBookToNewAccount(newAccountOnA.state.data.accountId, resultOfMoveToAccountOnA))
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
        val moveToAccountOnCFuture = a.startFlow(MoveLoanBookToNewAccount(createdAccountOnC.state.data.accountId, movedToNewAccountOnABrick))
        network.runNetwork()
        //gold now owned by account on C
        val resultOfMoveOnC = moveToAccountOnCFuture.getOrThrow()

        Assert.assertThat(resultOfMoveOnC.state.data.owningAccount, `is`(equalTo(createdAccountOnC.state.data.signingKey)))
    }


    @Test
    fun `it should be possible to query holdings by account on a single node`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        val account1Future = accountServiceOnA.createAccount("ACCOUNT_1")
        val account2Future = accountServiceOnA.createAccount("ACCOUNT_2")
        val account3Future = accountServiceOnA.createAccount("ACCOUNT_3")

        network.runNetwork()

        val account1Created = account1Future.getOrThrow()
        val miningFuture1 = a.startFlow(IssueLoanBookFlow(100, account1Created))
        val miningFuture2 = a.startFlow(IssueLoanBookFlow(101, account2Future.getOrThrow()))
        val miningFuture3 = a.startFlow(IssueLoanBookFlow(102, account3Future.getOrThrow()))

        network.runNetwork()

        miningFuture1.getOrThrow()
        miningFuture2.getOrThrow()


        val loansInAccount1 = accountServiceOnA.accountVaultQuery(
            account1Created.state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        )

        Assert.assertThat(loansInAccount1.size, `is`(1))
        val loanBookInAccount1 = loansInAccount1.first().state.data as LoanBook
        Assert.assertThat(loanBookInAccount1.owningAccount, `is`(account1Created.state.data.signingKey))
        Assert.assertThat(loanBookInAccount1.valueInUSD, `is`(100L))


        val moveToAccount1Future = a.startFlow(MoveLoanBookToNewAccount(account1Created.state.data.accountId, miningFuture2.getOrThrow()))
        network.runNetwork()
        moveToAccount1Future.getOrThrow()

        val loansInAccount1AfterMove = accountServiceOnA.accountVaultQuery(
            account1Created.state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        )

        //THERE SHOULD NOW BE 2 LOAN BOOKS
        Assert.assertThat(loansInAccount1AfterMove.size, `is`(2))
        //WITH VALUE 100 + 101
        Assert.assertThat(loansInAccount1AfterMove.sumByLong { (it.state.data as LoanBook).valueInUSD }, `is`(201L))


        //ACCOUNT 3 SHOULD BE LEFT UNCHANGED
        val account3States = accountServiceOnA.accountVaultQuery(
            account3Future.getOrThrow().state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>

        Assert.assertThat(account3States.size, `is`(1))
        Assert.assertThat(account3States.first().state.data.valueInUSD, `is`(102L))
        Assert.assertThat(account3States.first().state.data.owningAccount, `is`(account3Future.getOrThrow().state.data.signingKey))
    }

    @Test
    fun `it should be possible to query holdings on a node which recieved a carbon copy`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnB = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        //CREATE AN ACCOUNT ON A WITH A CARBON COPY RECEIVER OF B
        val account1Future = accountServiceOnA.createAccount("ACCOUNT_1", listOf(b.info.legalIdentities.first()))
        val account2Future = accountServiceOnA.createAccount("ACCOUNT_2")
        val account3Future = accountServiceOnA.createAccount("ACCOUNT_3")

        network.runNetwork()

        val account1Created = account1Future.getOrThrow()
        val miningFuture1 = a.startFlow(IssueLoanBookFlow(100, account1Created))
        val miningFuture2 = a.startFlow(IssueLoanBookFlow(101, account2Future.getOrThrow()))

        network.runNetwork()

        miningFuture1.getOrThrow()
        miningFuture2.getOrThrow()

        //QUERY ON B - IT SHOULD HAVE RECEIVED AN UPDATE AND UPDATED IT'S RECORDS
        val loansInAccount1 = b.transaction {
            accountServiceOnB.accountVaultQuery(
                account1Created.state.data.accountId,
                QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
            )
        }

        Assert.assertThat(loansInAccount1.size, `is`(1))
        val loanBookInAccount1 = loansInAccount1.first().state.data as LoanBook
        Assert.assertThat(loanBookInAccount1.owningAccount, `is`(account1Created.state.data.signingKey))
        Assert.assertThat(loanBookInAccount1.valueInUSD, `is`(100L))

        val moveToAccount1Future = a.startFlow(MoveLoanBookToNewAccount(account1Created.state.data.accountId, miningFuture2.getOrThrow()))
        network.runNetwork()
        moveToAccount1Future.getOrThrow()

        val loansInAccount1AfterMove = accountServiceOnB.accountVaultQuery(
            account1Created.state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        )

        //THERE SHOULD NOW BE 2 LOAN BOOKS
        Assert.assertThat(loansInAccount1AfterMove.size, `is`(2))
        //WITH VALUE 100 + 101
        Assert.assertThat(loansInAccount1AfterMove.sumByLong { (it.state.data as LoanBook).valueInUSD }, `is`(201L))

        val account2States = accountServiceOnB.accountVaultQuery(
            account2Future.getOrThrow().state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>

        //B SHOULD NOT KNOW ANYTHING ABOUT ACCOUNT 2 OR ACCOUNT 3
        val account3States = accountServiceOnB.accountVaultQuery(
            account3Future.getOrThrow().state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>

        Assert.assertThat(account2States.size, `is`(0))
        Assert.assertThat(account3States.size, `is`(0))
    }


    @Test
    fun `it should be possible to split a loan`() {
        val accountServiceOnA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceOnB = b.services.cordaService(KeyManagementBackedAccountService::class.java)
        val account1Future = accountServiceOnA.createAccount("ACCOUNT_1", listOf(b.info.legalIdentities.first()))
        network.runNetwork()
        val account1Created = account1Future.getOrThrow()
        val miningFuture1 = a.startFlow(IssueLoanBookFlow(100, account1Created))
        network.runNetwork()
        val loanBook = miningFuture1.getOrThrow()

        val splitFuture = a.startFlow(SplitLoanFlow(loanBook, 51))
        network.runNetwork()
        val splitLoanBooks = splitFuture.getOrThrow()

        val loansInAccount1OnB = accountServiceOnB.accountVaultQuery(
            account1Future.getOrThrow().state.data.accountId,
            QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>

        Assert.assertThat(splitLoanBooks.sortedBy { it.state.data.valueInUSD }, `is`(equalTo(loansInAccount1OnB.sortedBy { it.state.data.valueInUSD })))

    }
}