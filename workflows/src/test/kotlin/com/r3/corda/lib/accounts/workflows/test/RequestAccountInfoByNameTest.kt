package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.workflows.flows.*
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

class RequestAccountInfoByNameTest {

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

    /*
        Should return the account info to the host which been requested
     */

    @Test
    fun `should send account info to requester`() {

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Call RequestAccountInfoByName from host A so that host A can request accountB's info
        val accountBInfo = nodeA.startFlow(RequestAccountInfoByName("Test_AccountB", nodeB.info.legalIdentities.first())).runAndGet(network)
        print(accountBInfo)

        //Checking if accountBInfo's name will be equal to the name with which the account is created
        Assert.assertEquals(accountBInfo?.name, "Test_AccountB")

        //Checking if accountBInfo's name will be equal to the name of the account created
        Assert.assertEquals(accountBInfo?.name, accountB.state.data.name)
    }

    /*
        Should throw error if the requested account is not of the host and compare expected account's name with
         actual account's name
     */


    @Test(expected = AssertionError::class)
    fun `should throw error if the name of account passed is wrong and compare the expected account's and actual account's name`() {

        //Create an account in host B
        val accountB = nodeB.startFlow(CreateAccount("Test_AccountB")).runAndGet(network)

        //Create an account in host C
        val accountC = nodeC.startFlow(CreateAccount("Test_AccountC")).runAndGet(network)

        //To avail the account info of account B for node A, passing name of account C which is wrong name
        val accountBInfo = nodeA.startFlow(RequestAccountInfoByName(accountC.state.data.name, nodeB.info.legalIdentities.first())).runAndGet(network)

        //Comparing actual account's name with expected account(account B)'s name
        val resultOfAccountIdentifierComparison = Assert.assertEquals(accountBInfo?.name, accountB.state.data.name)

        //result will throw error since the name comparison do not match
        assertFailsWith<AssertionError> { resultOfAccountIdentifierComparison }

    }

    /*
        Should throw error if the host passed is wrong and compare expected account's name with
        actual account's name
     */


    @Test(expected = AssertionError::class)
    fun `should throw error if the account's host is wrong and compare expected account's and actual account's name`() {

        //Create an account in host A
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)

        //To get the account info of accountA, passing host as C which is the wrong host
        val accountAInfo = nodeB.startFlow(RequestAccountInfoByName(accountA.state.data.name, nodeC.info.legalIdentities.first())).runAndGet(network)

        //Comparing actual account's name with expected account(account A)'s name
        val resultOfAccountIdentifierComparison = Assert.assertEquals(accountAInfo?.name, accountA.state.data.name)

        //result will throw error since the name comparison do not match
        assertFailsWith<AssertionError> { resultOfAccountIdentifierComparison }
    }

    /*
        This testcase check when pass wrong name of the account, the result will be null
     */

    @Test
    fun `should return null if account is not found when searching by name`() {
        
        //Create an account in host C
        val accountC = nodeC.startFlow(CreateAccount("Test_AccountC")).runAndGet(network)

        //To avail the account info of account B for node A, passing name of account C which will throw an error
        val accountBInfo = nodeA.startFlow(RequestAccountInfoByName(accountC.state.data.name, nodeB.info.legalIdentities.first())).runAndGet(network)
        print(accountBInfo)

        //accountBInfo will be null if the name of account entered is wrong
        Assert.assertEquals(accountBInfo,null)

    }

}