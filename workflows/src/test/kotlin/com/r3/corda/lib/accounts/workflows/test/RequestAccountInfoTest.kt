package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.lang.AssertionError
import kotlin.test.assertFailsWith

class RequestAccountInfoTest {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode

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
        nodeC = network.createPartyNode()

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

    /*
        Should return the account info to the host which been requested
     */

    @Test
    fun `should send account info to requester`() {

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Call RequestAccountInfo from host A so that host A can request accountB's info
        val accountBInfo = nodeA.startFlow(RequestAccountInfo(accountB.uuid, nodeB.info.legalIdentities.first())).runAndGet(network)
        print(accountBInfo)

        //Checking if accountBInfo's name will be equal to the name with which the account is created
        Assert.assertEquals(accountBInfo?.name, "Test_AccountB")

        //Checking if accountBInfo's identifier will be equal to the identifier of the account created
        Assert.assertEquals(accountBInfo?.identifier, accountB.state.data.identifier)
    }

    /*
        Should throw error if the requested account is not of the host and compare expected account's identifer with
         actual account's identifier
     */


    @Test(expected = AssertionError::class)
    fun `should throw error if the UUID of account passed is wrong and compare the expected account's and actual account's identifier`() {

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Create an account in host C
        val accountC = nodeC.startFlow(CreateAccount("Test_AccountC")).runAndGet(network)

        //To avail the account info of account B for node A, passing UUID of account C which is wrong UUID
        val accountBInfo = nodeA.startFlow(RequestAccountInfo(accountC.uuid, nodeB.info.legalIdentities.first())).runAndGet(network)

        //Comparing actual account's identifier with expected account(account B)'s identifier
        val resultOfAccountIdentifierComparison = Assert.assertEquals(accountBInfo?.identifier, accountB.state.data.identifier)

        //result will throw error since the identifier comparison do not match
        assertFailsWith<AssertionError> { resultOfAccountIdentifierComparison }

    }

    /*
        Should throw error if the host passed is wrong and compare expected account's identifer with
        actual account's identifier
     */


    @Test(expected = AssertionError::class)
    fun `should throw error if the account's host is wrong and compare expected account's and actual account's identifier`() {

        //Create an account in host A
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //To get the account info of accountA, passing host as C which is the wrong host
        val accountAInfo = nodeB.startFlow(RequestAccountInfo(accountA.uuid, nodeC.info.legalIdentities.first())).runAndGet(network)

        //Comparing actual account's identifier with expected account(account A)'s identifier
        val resultOfAccountIdentifierComparison = Assert.assertEquals(accountAInfo?.identifier, accountA.state.data.identifier)

        //result will throw error since the identifier comparison do not match
        assertFailsWith<AssertionError> { resultOfAccountIdentifierComparison }
    }

    /*
        This testcase check when pass wrong UUID of the account, the result will be null
     */

    @Test
    fun `should return null if account id is not found when searching by UUID`() {

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Create an account in host C
        val accountC = nodeC.startFlow(CreateAccount("Test_AccountC")).runAndGet(network)

        //To avail the account info of account B for node A, passing UUID of account C which will throw an error
        val accountBInfo = nodeA.startFlow(RequestAccountInfo(accountC.uuid, nodeB.info.legalIdentities.first())).runAndGet(network)
        print(accountBInfo)

        //accountBInfo will be null if the UUID of account entered is wrong
        Assert.assertEquals(accountBInfo,null)

    }


}