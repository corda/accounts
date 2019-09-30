package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateAccountFlowTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode

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
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    //host node will create an account
    @Test
    fun `should be possible to create an account`() {
        //host node A will create an account
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        val createAccountInfoA = nodeA.services.vaultService.queryBy(AccountInfo::class.java).states.single()

        //check whether the created account in node A is same as that in the AccountInfo of host node `A
        Assert.assertTrue(createAccountInfoA == accountA)
    }

    //same host node will not create different accounts with same name
    @Test
    fun `should not be possible to create accounts with same name`() {
        //host node A will create an account
        nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        //host node A will try to create another account with same name as they have created earlier
        val accountA2= nodeA.startFlow(CreateAccount("Test_AccountA1"))

        assertFailsWith<IllegalArgumentException> { accountA2.getOrThrow() }
    }


    //different host node will create accounts having same name
    @Test
    fun `should be possible to create accounts with same name with different hosts`() {
        //host node A will create an account
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        //host node B will create an account with same name as node A used to create their account
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        val createAccountInfoA = nodeA.services.vaultService.queryBy(AccountInfo::class.java).states.single()
        val createAccountInfoB = nodeB.services.vaultService.queryBy(AccountInfo::class.java).states.single()

        //check whether the created account in node A is same as that in the AccountInfo of host node A
        Assert.assertTrue(createAccountInfoA == accountA)

        //check whether the created account in node B is same as that in the AccountInfo of host node B
        Assert.assertTrue(createAccountInfoB == accountB)


    }

    //same host node will create diiferent accounts with different name
    @Test
    fun `should be possible to create more than an account by a host`() {

        //host node A will create an account
        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)

        //host node A will create another account with different name
        val accountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)

        //check whether the AccountInfo of  host node A matches with the created accounts in node A
        Assert.assertThat(nodeA.services.vaultService.queryBy(AccountInfo::class.java).states, Matchers.containsInAnyOrder(accountA1, accountA2))

    }

    //Should return null if the account name to search is wrong, when searching by account name
    @Test
    fun `should return empty list if account name is wrong when searching by account name`() {

        //Create two accounts in node A
        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1"))
        val accountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2"))
        network.runNetwork()
        accountA1.getOrThrow()
        accountA2.getOrThrow()

        val accountService = nodeA.services.accountService
        nodeA.transaction {
            //Pass wrong account name as argument to get the account info
            val foundAccount = accountService.accountInfo("TestAccountA1")
            //Checking if foundAccount is null since account name was wrongly entered
            assertEquals(foundAccount, emptyList())
        }
    }

}
