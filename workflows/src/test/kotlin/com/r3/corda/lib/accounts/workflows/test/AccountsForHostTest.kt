package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.AccountsForHost
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AccountsForHostTest {

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

    fun StartedMockNode.identity() = info.legalIdentities.first()

    fun StartedMockNode.shareAccountInfo(accountInfo: StateAndRef<AccountInfo>, recipient: StartedMockNode): CordaFuture<Unit> {
        return startFlow(ShareAccountInfo(accountInfo, listOf(recipient.identity())))
    }

    /* This testcase checks that AccountsForHost will list all accounts created in a host */

    @Test
    fun `should list all hosted accounts`() {

        //Create two accounts in host A
        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val accountA2 =nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)

        //Call AccountsForHost from host A which returns the accounts of host A
        val hostAccountInfoA = nodeA.startFlow(AccountsForHost(nodeA.identity())).runAndGet(network)

        //Checking if first account in the list hostAccountInfoA is accountA1
        Assert.assertEquals(accountA1, hostAccountInfoA[0])

        //Checking if first account in the list hostAccountInfoA is accountA2
        Assert.assertEquals(accountA2, hostAccountInfoA[1])

        //Checking if hostAccountInfoA contain accountA1 and accountA2
        Assert.assertEquals(listOf(accountA1,accountA2), hostAccountInfoA)

    }

    /*
        This testcase checks that AccountsForHost method will not contain accounts hosted by other hosts.
        Only hosted accounts will be returned by this flow.
    */

    @Test
    fun `should not list accounts that does not belong to the host`() {

        //Create two accounts in host A
        val accountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val accountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)

        //Create an account in host B
        val accountB1 = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Call AccountsForHost from host A which returns the accounts of host A
        val hostAccountInfoA = nodeA.startFlow(AccountsForHost(nodeA.identity())).runAndGet(network)

        //Call AccountsForHost from host B which returns the accounts of host B
        val hostAccountInfoB = nodeB.startFlow(AccountsForHost(nodeB.identity())).runAndGet(network)

        //Checking if hostAccountInfoA contain both accountA1 and accountA2
        Assert.assertEquals(listOf(accountA1,accountA2), hostAccountInfoA)

        //Checking if hostAccountInfoB contains accountB1
        Assert.assertEquals(listOf(accountB1), hostAccountInfoB)

        //Checking that hostAccountInfoA does not contain accountB1
        Assert.assertNotEquals(listOf(accountB1), hostAccountInfoA)

        //Checking that hostAccountInfoB does not contain accountA1 and accountA2
        Assert.assertNotEquals(listOf(accountA1, accountA2), hostAccountInfoB)

    }

    /*
        This testcase checks if AccountForHost does not look-up on shared accounts. The flow will only
        return the accounts hosted by the host.
    */

    @Test
    fun `should not lookup shared accounts`() {

        //Create an account in host A
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Share accountA with host B
        nodeA.startFlow(ShareAccountInfo(accountA, listOf(nodeB.identity()))).runAndGet(network)

        //Call AccountsForHost from host A which return all hosted accounts of A
        val hostAccountInfoA = nodeA.startFlow(AccountsForHost(nodeA.identity())).runAndGet(network)

        //Call AccountsForHost from host B which return all hosted accounts of B
        val hostAccountInfoB = nodeB.startFlow(AccountsForHost(nodeB.identity())).runAndGet(network)

        //Checking if first account in hostAccountInfoA is accountA
        Assert.assertEquals(accountA, hostAccountInfoA[0])

        //Checking if first account in hostAccountInfoB is accountB
        Assert.assertEquals(accountB, hostAccountInfoB[0])

        //Checking that hostAccountInfoA is the list in which only accountA is there
        Assert.assertEquals(listOf(accountA), hostAccountInfoA)

        //Checking that hostAccountInfoA does not contain accountB after that account being shared by B
        Assert.assertNotEquals(listOf(accountA, accountB), hostAccountInfoA)
    }


    /*
            This testcase checks if it is possible to look-up an account by its UUID
     */

    @Test
    fun `should be possible to lookup account by UUID`() {

        //Create an account in host A
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //Create accountService for host A
        val accountService = nodeA.services.accountService

        //Checking if it is possible to look-up the account info using UUID and again checking if that account info is of accountA
        nodeA.transaction {
            assertThat(accountService.accountInfo(accountA.uuid), `is`(accountA))
        }
    }

    /*
    This testcase checks if its possible to look-up an account by its account name
     */

    @Test
    fun `should be possible to lookup account by name`() {

        //Create an account in host A
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //Create accountService for host A
        val accountService = nodeA.services.accountService

        //Checking if it is possible to look-up the account info using name and again checking if that account info is of accountA
        nodeA.transaction {
            assertEquals(accountA, accountService.accountInfo("Test_AccountA").single())
        }
    }

}