package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.*
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
import java.util.*
import kotlin.test.assertEquals

class AccountInfoTests {

    private lateinit var network: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var nodeC: StartedMockNode
    private lateinit var accountOnNodeA: StateAndRef<AccountInfo>
    private lateinit var accountOnNodeB: StateAndRef<AccountInfo>
    private lateinit var accountOnNodeC: StateAndRef<AccountInfo>

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

        accountOnNodeA = nodeA.startFlow(CreateAccount("Account_On_A")).runAndGet(network)
        accountOnNodeB = nodeB.startFlow(CreateAccount("Account_On_B")).runAndGet(network)
        accountOnNodeC = nodeC.startFlow(CreateAccount("Account_On_C")).runAndGet(network)

        //Node A will share the created account with Node B
        nodeA.startFlow(ShareAccountInfo(accountOnNodeA, listOf(nodeB.identity()))).runAndGet(network)

        //Node B will share the created account with Node A
        nodeB.startFlow(ShareAccountInfo(accountOnNodeB, listOf(nodeA.identity()))).runAndGet(network)
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `Get accounts by UUID`() {
        val accountInfoAfromA = nodeA.startFlow(AccountInfoByUUID(accountOnNodeA.uuid)).runAndGet(network)
        val accountInfoBfromA = nodeA.startFlow(AccountInfoByUUID(accountOnNodeB.uuid)).runAndGet(network)
        Assert.assertEquals(accountOnNodeA, accountInfoAfromA)
        Assert.assertEquals(accountOnNodeB, accountInfoBfromA)

        val accountInfoAfromB = nodeB.startFlow(AccountInfoByUUID(accountOnNodeA.uuid)).runAndGet(network)
        val accountInfoBfromB = nodeB.startFlow(AccountInfoByUUID(accountOnNodeB.uuid)).runAndGet(network)
        Assert.assertEquals(accountOnNodeA, accountInfoAfromB)
        Assert.assertEquals(accountOnNodeB, accountInfoBfromB)
    }

    @Test
    fun `Create and query accounts by externalId`() {
        // Create the account with externalId on A
        val name = "Account_WithExternalId_On_A"
        val externalId = UUID.randomUUID().toString()
        val accountInfo = nodeA.startFlow(CreateAccount(name, externalId)).runAndGet(network)

        // Check for expected external ID
        Assert.assertEquals(externalId, accountInfo.id.externalId)

        // Node A will share the created account with Node B
        nodeA.startFlow(ShareAccountInfo(accountInfo, listOf(nodeB.identity()))).runAndGet(network)
        // Node C requests the created account from A
        nodeC.startFlow(RequestAccountInfo(accountInfo.uuid, nodeA.info.legalIdentities.first())).runAndGet(network)

        // Check query stack by external ID works for all A, B and C
        val accountInfosA = nodeA.startFlow(AccountInfoByExternalId(externalId)).runAndGet(network)
        val accountInfosB = nodeB.startFlow(AccountInfoByExternalId(externalId)).runAndGet(network)
        val accountInfosC = nodeC.startFlow(AccountInfoByExternalId(externalId)).runAndGet(network)
        Assert.assertEquals(accountInfo, accountInfosA.single())
        Assert.assertEquals(accountInfo, accountInfosB.single())
        Assert.assertEquals(accountInfo, accountInfosC.single())
    }

    @Test
    fun `Accounts not shared are not found`() {
        //Validate that the account on C exists first
        val accountInfoCfromC = nodeC.startFlow(AccountInfoByUUID(accountOnNodeC.uuid)).runAndGet(network)
        Assert.assertEquals(accountOnNodeC, accountInfoCfromC)

        //Verify that we get null when looking for AccountC on a node we haven't shared it with
        val accountInfoCfromA = nodeA.startFlow(AccountInfoByUUID(accountOnNodeC.uuid)).runAndGet(network)
        Assert.assertNull(accountInfoCfromA)
    }

    @Test
    fun `Get accounts by Name`() {
        val accountInfoAfromA = nodeA.startFlow(AccountInfoByName(accountOnNodeA.state.data.name)).runAndGet(network)
        val accountInfoBfromA = nodeA.startFlow(AccountInfoByName(accountOnNodeB.state.data.name)).runAndGet(network)
        Assert.assertEquals(accountOnNodeA, accountInfoAfromA.single())
        Assert.assertEquals(accountOnNodeB, accountInfoBfromA.single())

        val accountInfoAfromB = nodeB.startFlow(AccountInfoByName(accountOnNodeA.state.data.name)).runAndGet(network)
        val accountInfoBfromB = nodeB.startFlow(AccountInfoByName(accountOnNodeB.state.data.name)).runAndGet(network)
        Assert.assertEquals(accountOnNodeA, accountInfoAfromB.single())
        Assert.assertEquals(accountOnNodeB, accountInfoBfromB.single())
    }

    @Test
    fun `Get accounts by PublicKey`() {
        val keyA = nodeA.startFlow(RequestKeyForAccount(accountOnNodeA.state.data)).runAndGet(network).owningKey

        val accountInfoOnA = nodeA.startFlow(AccountInfoByKey(keyA)).runAndGet(network)
        Assert.assertEquals(accountOnNodeA, accountInfoOnA)

        //Note that we deliberately get the key from node A then do the lookup on Node B
        val keyB = nodeA.startFlow(RequestKeyForAccount(accountOnNodeB.state.data)).runAndGet(network).owningKey
        val accountInfoOnB = nodeB.startFlow(AccountInfoByKey(keyB)).runAndGet(network)
        Assert.assertEquals(accountOnNodeB, accountInfoOnB)
    }

    @Test
    fun `should retrieve other node's public key to account id mapping via identity service`() {
        // B generates a new key.
        val key = nodeB.services.keyManagementService.freshKey()
        val id = UUID.randomUUID()
        // A maps B's key to an ID.
        nodeA.transaction {
            nodeA.services.identityService.registerKey(key, nodeB.info.legalIdentities.first(), id)
        }
        // A can retrieve the ID from the key.
        val result = nodeA.transaction { nodeA.services.identityService.externalIdForPublicKey(key) }
        assertEquals(id, result)
    }

}
