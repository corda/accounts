package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.*
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.Crypto
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

class AccountKeysTests {

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


    @Test
    fun `should create multiple keys for an account when requested`() {

        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).let {
            network.runNetwork()
            it.getOrThrow()
        }


        val keyToUse1 = b.startFlow(RequestKeyForAccount(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse2 = b.startFlow(RequestKeyForAccount(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse3 = b.startFlow(RequestKeyForAccount(account2.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val foundKeysForAccount1 = a.transaction {
            findKeysForAccount(account1)
        }

        val foundKeysForAccount2 = a.transaction {
            findKeysForAccount(account2)
        }
        Assert.assertThat(foundKeysForAccount1, containsInAnyOrder(keyToUse1.owningKey, keyToUse2.owningKey))
        Assert.assertThat(foundKeysForAccount2, containsInAnyOrder(keyToUse3.owningKey))
    }

    private fun findKeysForAccount(account2: StateAndRef<AccountInfo>): List<PublicKey>? {
        val em = currentDBSession().entityManagerFactory.createEntityManager()
        return em?.let {
            val query = em.createQuery(
                    """
                            select a.$persistentKey_publicKey
                            from $persistentKey a, $publicKeyHashToExternalId b
                            where b.$publicKeyHashToExternalId_externalId = :uuid
                                and b.$publicKeyHashToExternalId_publicKeyHash = a.$persistentKey_publicKeyHash
                        """,
                    ByteArray::class.java
            )
            query.setParameter("uuid", account2.state.data.id.id)
            query.resultList.map { Crypto.decodePublicKey(it) }
        }
    }

    @Test
    fun `should be possible to lookup account by previously used key`() {

        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val account2 = a.startFlow(CreateAccount("Stefano_Account2")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse1 = b.startFlow(RequestKeyForAccount(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val keyToUse2 = b.startFlow(RequestKeyForAccount(account2.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)

        a.transaction {
            Assert.assertThat(accountService.accountInfo(keyToUse1.owningKey), `is`(account1))
            Assert.assertThat(accountService.accountInfo(keyToUse2.owningKey), `is`(account2))
        }

    }

}