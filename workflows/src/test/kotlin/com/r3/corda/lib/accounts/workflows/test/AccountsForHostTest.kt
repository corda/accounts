package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.AccountsForHost
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
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture

class AccountsForHostTest {

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

    /* This testcase checks that AccountsForHost will list all accounts created in a host */

    @Test
    fun `should list all hosted accounts`() {

        //Create two accounts in host A
        val account1 = a.startFlow(CreateAccount("Host_Account1")).runAndGet(network)
        val account2 = a.startFlow(CreateAccount("Host_Account2")).runAndGet(network)

        //Call AccountsForHost from host A which returns the accounts of host A
        val hostAccountsA = a.startFlow(AccountsForHost(a.identity())).runAndGet(network)

        //Checking if first account in the list hostAccountsA is account1
        Assert.assertEquals(account1, hostAccountsA[0])

        //Checking if first account in the list hostAccountsA is account2
        Assert.assertEquals(account2, hostAccountsA[1])

        //Checking if hostAccountsA contain account1 and account2
        Assert.assertEquals(listOf(account1,account2), hostAccountsA)

    }

    /*
        This testcase checks that AccountsForHost method will not contain accounts hosted by other hosts.
        Only hosted accounts will be returned by this flow.
    */

    @Test
    fun `should not list accounts that does not belong to the host`() {

        //Create two accounts in host A
        val account1 = a.startFlow(CreateAccount("Host_Account1")).runAndGet(network)
        val account2 = a.startFlow(CreateAccount("Host_Account2")).runAndGet(network)

        //Create an account in host B
        val account3 = b.startFlow(CreateAccount("Host_Account3")).runAndGet(network)

        //Call AccountsForHost from host A which returns the accounts of host A
        val hostAccountsA = a.startFlow(AccountsForHost(a.identity())).runAndGet(network)

        //Call AccountsForHost from host B which returns the accounts of host B
        val hostAccountsB = b.startFlow(AccountsForHost(b.identity())).runAndGet(network)

        //Checking if hostAccountsA contain both account1 and account2
        Assert.assertEquals(listOf(account1,account2), hostAccountsA)

        //Checking if hostAccountsB contains account3
        Assert.assertEquals(listOf(account3), hostAccountsB)

        //Checking that hostAccountsA does not contain account3
        Assert.assertNotEquals(listOf(account3), hostAccountsA)

        //Checking that hostAccountsB does not contain account1 and account2
        Assert.assertNotEquals(listOf(account1, account2), hostAccountsB)

    }

    /*
        This testcase checks if AccountForHost does not look-up on shared accounts. The flow will only
        return the accounts hosted by the host.
    */

    @Test
    fun `should not lookup shared accounts`() {

        //Create an account in host A
        val account1 = a.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //Create an account in host B
        val account2 = b.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Share account1 with host B
        a.startFlow(ShareAccountInfo(account1, listOf(b.identity()))).runAndGet(network)

        //Call AccountsForHost from host A which return all hosted accounts of A
        val hostAccountInfoA = a.startFlow(AccountsForHost(a.identity())).runAndGet(network)

        //Call AccountsForHost from host B which return all hosted accounts of B
        val hostAccountInfoB = b.startFlow(AccountsForHost(b.identity())).runAndGet(network)

        //Checking if first account in hostAccountInfoA is account1
        Assert.assertEquals(account1, hostAccountInfoA[0])

        //Checking if first account in hostAccountInfoB is account2
        Assert.assertEquals(account2, hostAccountInfoB[0])

        //Checking that hostAccountInfoA is the list in which only account1 is there
        Assert.assertEquals(listOf(account1), hostAccountInfoA)

        //Checking that hostAccountInfoA does not contain account2 after that account being shared by B
        Assert.assertNotEquals(listOf(account1, account2), hostAccountInfoA)
    }


    /*
            This testcase checks if it is possible to look-up an account by its UUID
     */

    @Test
    fun `should be possible to lookup account by UUID`() {

        //Create an account in host A
        val account1 = a.startFlow(CreateAccount("Account1")).runAndGet(network)

        //Create accountService for host A
        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        //Checking if it is possible to look-up the account info using UUID and again checking if that account info is of account1
        a.transaction {
            Assert.assertThat(accountService.accountInfo(account1.uuid), `is`(account1))
        }
    }

    /*
    This testcase checks if its possible to look-up an account by its account name
     */

    @Test
    fun `should be possible to lookup account by name`() {

        //Create an account in host A
        val account1 = a.startFlow(CreateAccount("Account1")).runAndGet(network)

        //Create accountService for host A
        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        //Checking if it is possible to look-up the account info using name and again checking if that account info is of account1
        a.transaction {
            Assert.assertThat(accountService.accountInfo("Account1"), `is`(account1))
        }
    }

}