package net.corda.accounts.flows.test

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.flows.CreateAccount
import net.corda.accounts.workflows.flows.ReceiveStateForAccountFlow
import net.corda.accounts.workflows.flows.RequestKeyForAccountFlow
import net.corda.accounts.workflows.services.AccountService
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
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
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class ShareAccountTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                listOf(
                        "net.corda.accounts.contracts",
                        "net.corda.accounts.workflows"
                ), MockNetworkParameters(
                networkParameters = testNetworkParameters(
                        minimumPlatformVersion = 4
                )
        )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()

        a.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)
        b.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `should send account info to party provided`() {
        val future = a.startFlow(CreateAccount("Stefano_Account"))
        network.runNetwork()
        val result = future.getOrThrow()
        val storedAccount = a.transaction {
            val storedAccountInfo = a.services.vaultService.queryBy(AccountInfo::class.java).states.single()
            Assert.assertTrue(storedAccountInfo == result)
            storedAccountInfo
        }

        val aAccountService = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        a.transaction {
            val foundAccount = aAccountService.accountInfo(result.state.data.id)
            Assert.assertThat(foundAccount, `is`(storedAccount))
        }

        aAccountService.shareAccountInfoWithParty(result.state.data.id, b.info.legalIdentities.first()).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val bAccountService = b.services.cordaService(KeyManagementBackedAccountService::class.java)

        val accountOnB = b.transaction {
            bAccountService.accountInfo(result.state.data.id)
        }
        Assert.assertThat(accountOnB, `is`(storedAccount))
    }


    @Test
    fun `should share a state and all required account info to party`() {
        val result = a.startFlow(CreateAccount("Stefano_Account")).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val ownedByAccountState = a.startFlow(IssueFlow(result.state.data.id)).let {
            network.runNetwork()
            it.getOrThrow()
        }


        val accountServiceOnA = a.accountService()

        accountServiceOnA.shareStateAndSyncAccounts(ownedByAccountState, b.identity()).let {
            network.runNetwork()
            it.getOrThrow()
        }

        val accountServiceOnB = b.accountService()

        b.transaction {
            Assert.assertThat(accountServiceOnB.accountInfo(result.state.data.id), `is`(result))
            Assert.assertThat(accountServiceOnB.accountInfo(ownedByAccountState.state.data.owner.owningKey), `is`(result))
            //now check that nodeB knows who the account key really is
            Assert.assertThat(b.services.identityService.wellKnownPartyFromAnonymous(ownedByAccountState.state.data.owner), `is`(a.identity()))
        }
    }
}

private fun StartedMockNode.accountService(): AccountService {
    return this.services.cordaService(KeyManagementBackedAccountService::class.java)
}


@BelongsToContract(TestContract::class)
data class TestState(val owner: AbstractParty, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(owner, issuer)
}

class TestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}

object ISSUE : CommandData

class IssueFlow(private val owningAccount: UUID) : FlowLogic<StateAndRef<TestState>>() {
    @Suspendable
    override fun call(): StateAndRef<TestState> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accountInfo = accountService.accountInfo(owningAccount) ?: throw IllegalStateException()
        val keyToUse = subFlow(RequestKeyForAccountFlow(accountInfo.state.data))
        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(TestState(keyToUse, ourIdentity))
                .addCommand(ISSUE, ourIdentity.owningKey)
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)
        val finalTx = subFlow(FinalityFlow(signedTx, emptyList()))
        return finalTx.tx.outRefsOfType<TestState>().single()
    }

}