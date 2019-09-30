package com.r3.corda.lib.accounts.workflows.test

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.services.AccountService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertFailsWith

class ShareStateAndSyncAccountsFlowTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            cordappPackages = listOf("com.r3.corda.lib.accounts.contracts", "com.r3.corda.lib.accounts.workflows"),
            defaultParameters = MockNetworkParameters(networkParameters = testNetworkParameters(minimumPlatformVersion = 4))
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
    @Test
    fun `should share state and involved accounts with a different node`() {

        // Creating an account in nodeA.
        val result = nodeA.startFlow(CreateAccount("Test_Account")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val ownedByAccountState = nodeA.startFlow(IssueAccountFlow(result.uuid)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountServiceOnA = nodeA.accountService()

        // Sharing AccountInfo for ownedByAccountState StateAndRef and the StateAndRef to nodeB.
        nodeA.startFlow(ShareStateAndSyncAccounts(ownedByAccountState, nodeB.identity())).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountServiceOnB = nodeB.accountService()

        nodeB.transaction {
            // Check if the account 'result' is available in nodeB
            Assert.assertThat(accountServiceOnB.accountInfo(result.uuid), CoreMatchers.`is`(result))
            //now check IssueAccountState is available on nodeB
            Assert.assertEquals(nodeB.services.vaultService.queryBy(IssueAccountState::class.java).states.single(),ownedByAccountState)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `should only host share the state being account`() {

        // Creating an account in node A.
        val result = nodeA.startFlow(CreateAccount("Test_Account")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val ownedByAccountState = nodeA.startFlow(IssueAccountFlow(result.uuid)).let {
            network.runNetwork()
            it.getOrThrow()
        }

        // Sharing AccountInfo for ownedByAccountState StateAndRef and the StateAndRef to nodeB by nodeC.
        nodeC.startFlow(ShareStateAndSyncAccounts(ownedByAccountState, nodeB.identity())).let {
            network.runNetwork()
            it.getOrThrow()
        }
    }

}

private fun StartedMockNode.accountService(): AccountService {
    return this.services.accountService
}


@BelongsToContract(IssueAccountContract::class)
data class IssueAccountState(val owner: AbstractParty, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(owner, issuer)
}

class IssueAccountContract : Contract {
    override fun verify(tx: LedgerTransaction) = Unit
}

object ISSUE_Command : CommandData

class IssueAccountFlow(private val owningAccount: UUID) : FlowLogic<StateAndRef<IssueAccountState>>() {
    @Suspendable
    override fun call(): StateAndRef<IssueAccountState> {
        val accountInfo = accountService.accountInfo(owningAccount) ?: throw IllegalStateException()
        val keyToUse = subFlow(RequestKeyForAccount(accountInfo.state.data))
        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
            .addOutputState(IssueAccountState(keyToUse, ourIdentity))
            .addCommand(ISSUE_Command, ourIdentity.owningKey)
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        val finalTx = subFlow(FinalityFlow(signedTx, emptyList()))
        return finalTx.tx.outRefsOfType<IssueAccountState>().single()
    }

}