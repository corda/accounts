package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.AllAccounts
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareStateWithAccount
import com.r3.corda.lib.accounts.workflows.internal.accountObservedQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class ShareStateWithAccountFlowsTest {

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



    /*This test case check whether a shared state can be viewed by an account to which the state is shared*/
    @Test
    fun `should the shared state be seen by accounts to shared`() {

        //create an account on node A
        val testAccountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        //create an account on node B
        val testAccountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)
        //create another account on node A
        val testAccountToShare=nodeA.startFlow(CreateAccount("Test_Account_To_Share")).runAndGet(network)

        //share a state of an account with testAccount1
        nodeA.startFlow(ShareStateWithAccount(testAccountA2.state.data, testAccountToShare)).runAndGet(network)

        //get all state references of testAccount2
        val permissionedStatesForA2 = nodeA.transaction {
            nodeA.services.vaultService.accountObservedQueryBy<AccountInfo>(
                listOf(testAccountA2.uuid),
                QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        //get all state references of testAccount1
        val permissionedStatesForA1 = nodeA.transaction {
            nodeA.services.vaultService.accountObservedQueryBy<AccountInfo>(
                listOf(testAccountA1.uuid),
                QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        //create three accounts on node A
        nodeA.transaction {
            //checking reference of testAccountToShare is present in testAccount2
            Assert.assertEquals(permissionedStatesForA2, listOf(testAccountToShare.ref))
            //checking reference of testAccountToShare is not present in testAccount1
            Assert.assertEquals(permissionedStatesForA1.size, 0)

        }
    }
    /*
    This test case check whether a shared state can be viewed by an account to which the state is shared
*/
    @Test
    fun `should an account get the actual shared state`() {
        //create an account on node A
        val testAccountA1 = nodeA.startFlow(CreateAccount("Test_AccountA1")).runAndGet(network)
        val testAccountA2 = nodeA.startFlow(CreateAccount("Test_AccountA2")).runAndGet(network)

        //create an account on node B
        val testAccountB1 = nodeB.startFlow(CreateAccount("Test_AccountB1")).runAndGet(network)
        val testAccountB2 = nodeB.startFlow(CreateAccount("Test_AccountB2")).runAndGet(network)

        //create another account on node A
        nodeA.startFlow(CreateAccount("Test_Account_To_Share")).runAndGet(network)

        //share a state of an account with testAccount1
        nodeA.startFlow(ShareStateWithAccount(testAccountB1.state.data, testAccountA1)).runAndGet(network)
        nodeA.startFlow(ShareStateWithAccount(testAccountB1.state.data, testAccountA2 )).runAndGet(network)

        //get all state references of testAccountB1
        val permissionedStatesForB1 = nodeB.transaction {
            nodeA.services.vaultService.accountObservedQueryBy<AccountInfo>(
                listOf(testAccountB1.uuid),
                QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }

        //get all state references of testAccountB2
        val permissionedStatesForB2 = nodeB.transaction {
            nodeA.services.vaultService.accountObservedQueryBy<AccountInfo>(
                listOf(testAccountB2.uuid),
                QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(AccountInfo::class.java))
            ).states
        }.map { it.ref }
        nodeA.startFlow(AllAccounts()).runAndGet(network)
        val allAccountInfoB = nodeB.startFlow(AllAccounts()).runAndGet(network)

        //checking permissioned state for nodeB
        Assert.assertEquals(allAccountInfoB, listOf(testAccountB1, testAccountB2, testAccountA1, testAccountA2))
        //checking permissioned state for testAccountB1
        Assert.assertEquals(permissionedStatesForB1, listOf(testAccountA1.ref, testAccountA2.ref))
        //checking permissioned state for testAccountB2
        Assert.assertEquals(permissionedStatesForB2.size, 0)
    }
}

