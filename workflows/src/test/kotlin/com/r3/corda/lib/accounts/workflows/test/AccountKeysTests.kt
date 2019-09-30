package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

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

        val accountService = a.services.accountService

        val foundKeysForAccount1 = a.transaction {
            accountService.accountKeys(account1.state.data.identifier.id)
        }

        val foundKeysForAccount2 = a.transaction {
            accountService.accountKeys(account2.state.data.identifier.id)
        }
        Assert.assertThat(foundKeysForAccount1, containsInAnyOrder(keyToUse1.owningKey, keyToUse2.owningKey))
        Assert.assertThat(foundKeysForAccount2, containsInAnyOrder(keyToUse3.owningKey))
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

        val accountService = a.services.accountService

        a.transaction {
            Assert.assertThat(accountService.accountInfo(keyToUse1.owningKey), `is`(account1))
            Assert.assertThat(accountService.accountInfo(keyToUse2.owningKey), `is`(account2))
        }
    }

    @Test
    fun `verify keys can be looked up on both nodes involved in the key generation`() {
        val account1 = a.startFlow(CreateAccount("Stefano_Account1")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val newKey = b.startFlow(RequestKeyForAccount(account1.state.data)).let {
            network.runNetwork()
            it.getOrThrow()
        }.owningKey

        val accountId = account1.uuid

        a.transaction {
            // THis is A's key so we can use the KMS to look-up it's account.
            assertThat(a.services.identityService.externalIdForPublicKey(newKey)).isEqualTo(accountId)
        }

        b.transaction {
            // Keys from other nodes cannot be looked-up using the KMS but we can use the accounts service.
            assertThat(b.services.accountService.accountIdForKey(newKey)).isEqualTo(accountId)
        }
    }

    @Test
    fun `should be able to get all keys for an account`() {
        val futureA = a.startFlow(CreateAccount("Foo")).toCompletableFuture()
        network.runNetwork()
        val aFoo = futureA.getOrThrow()
        val aId = aFoo.state.data.linearId.id
        val notAId = UUID.randomUUID()

        val keyOne = a.services.keyManagementService.freshKey(aId)
        val keyTwo = a.services.keyManagementService.freshKey(aId)
        val keyThree = a.services.keyManagementService.freshKey(notAId)
        a.services.keyManagementService.freshKey() // Never returned.

        // TODO: Can remove this transaction block when we use the new API on KMS.
        a.transaction {
            val aKeys = a.services.accountService.accountKeys(aId)
            assertEquals(setOf(keyOne, keyTwo), aKeys.toSet())

            val notAKeys = a.services.accountService.accountKeys(notAId)
            assertEquals(setOf(keyThree), notAKeys.toSet())
        }
    }
}