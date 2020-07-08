package com.r3.corda.lib.accounts.examples.tokensTest

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import com.r3.corda.lib.ci.workflows.VerifyAndAddKey
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.FungibleState
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfidentialIntegrationTest {
    @Rule
    @JvmField
    val globalTimeout: Timeout = Timeout(5, TimeUnit.MINUTES)
    companion object {
        private val log = contextLogger()
    }

    private val partyA = NodeParameters(
            providedName = CordaX500Name("PartyA", "London", "GB"),
            additionalCordapps = listOf()
    )

    private val partyB = NodeParameters(
            providedName = CordaX500Name("PartyB", "London", "GB"),
            additionalCordapps = listOf()
    )

    private val partyC = NodeParameters(
            providedName = CordaX500Name("PartyC", "London", "GB"),
            additionalCordapps = listOf()
    )

    private val issuer = NodeParameters(
            providedName = CordaX500Name("Issuer", "London", "GB"),
            additionalCordapps = listOf()
    )


    private val nodeParams = listOf(partyA, partyB, partyC, issuer)

    private val defaultCorDapps = listOf(
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
            TestCordapp.findCordapp("com.r3.corda.lib.ci")
    )

    private val driverParameters = DriverParameters(
            startNodesInProcess = true,
            cordappsForAllNodes = defaultCorDapps,
            networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4)
    )

    fun NodeHandle.legalIdentity() = nodeInfo.legalIdentities.single()

    @Test
    fun `confidential identity test`() {

        driver(driverParameters) {
            val (nodeA, nodeB, nodeC, nodeI) = nodeParams.map { params -> startNode(params) }.transpose().getOrThrow()
            log.info("All nodes started up.")

            log.info("Creating an account on node A.")
            val createAccountOnA = nodeA.rpc.startFlow(::CreateAccount, "TestAccountA1").returnValue.getOrThrow()
            // Check that A recorded all the new accounts.
            val aAccountsQuery = nodeA.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            assertEquals(createAccountOnA, aAccountsQuery.single())

            log.info("Creating an account on node B.")
            val createAccountOnB = nodeB.rpc.startFlow(::CreateAccount, "TestAccountB1").returnValue.getOrThrow()
            // Check that B recorded all the new accounts.
            val bAccountsQuery = nodeB.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            assertEquals(createAccountOnB, bAccountsQuery.single())

            log.info("Sharing account info from node A to node I.")
            val sharingAccount = aAccountsQuery.single { it.state.data.name == "TestAccountA1" }
            CompletableFuture.allOf(
                    nodeA.rpc.startFlow(::ShareAccountInfo, sharingAccount, listOf(nodeI.legalIdentity())).returnValue.toCompletableFuture(),
                    nodeI.rpc.watchForTransaction(sharingAccount.ref.txhash).toCompletableFuture()
            ).getOrThrow()
            // Check that issuer stored the account info.
            val sharingAccountQuery = nodeI.rpc.vaultQuery(AccountInfo::class.java).states.single()
            assertEquals(sharingAccount, sharingAccountQuery)

            log.info("Creating a confidential identity to the shared account in node I.")
            val accountA1Anonymous = nodeI.rpc.startFlow(::RequestKeyForAccount, createAccountOnA.state.data).returnValue.getOrThrow()
            println("anonymous id: $accountA1Anonymous")

            log.info("node I issues tokens to account in node A.")
            val tokens = 100 of GBP issuedBy nodeI.legalIdentity() heldBy accountA1Anonymous
            val issuanceResult = nodeI.rpc.startFlow(::IssueTokens, listOf(tokens), emptyList()).returnValue.getOrThrow()
            nodeA.rpc.watchForTransaction(issuanceResult).getOrThrow()
            val accountA1TokensIssueQuery = nodeA.rpc.vaultQueryByCriteria(
                    QueryCriteria.VaultQueryCriteria(externalIds = listOf(sharingAccount.state.data.identifier.id)),
                    FungibleState::class.java
            ).states.single()
            assertEquals(tokens, accountA1TokensIssueQuery.state.data)

            val issueTxHolder = issuanceResult.coreTransaction.outRefsOfType<FungibleToken>().single().state.data.holder
            println("issue holder: $issueTxHolder")
            println("node A owning key: " + nodeA.legalIdentity().owningKey)
            assertNull(nodeB.rpc.wellKnownPartyFromAnonymous(issueTxHolder))

            // B to store the A account's key.
            log.info("node B request a new key mapping")
            val keyMapping = nodeB.rpc.startFlow(::VerifyAndAddKey, nodeA.legalIdentity(), issueTxHolder.owningKey).returnValue.getOrThrow()
            println("key mapping: $keyMapping")
            val partyResolvedFromNodeI = nodeI.rpc.wellKnownPartyFromAnonymous(issueTxHolder)
            println("partyResolvedFromNodeI: $partyResolvedFromNodeI")
            val partyResolvedFromNodeB = nodeB.rpc.wellKnownPartyFromAnonymous(issueTxHolder)
            println("partyResolvedFromNodeB: $partyResolvedFromNodeB")
            assertEquals(partyResolvedFromNodeI, partyResolvedFromNodeB)

            log.info("Share account of node B to node A")
            val sharingAccountToA = bAccountsQuery.single { it.state.data.name == "TestAccountB1" }
            CompletableFuture.allOf(
                    nodeB.rpc.startFlow(::ShareAccountInfo, sharingAccountToA, listOf(nodeA.legalIdentity())).returnValue.toCompletableFuture(),
                    nodeA.rpc.watchForTransaction(sharingAccountToA.ref.txhash).toCompletableFuture()
            ).getOrThrow()
            // Check that node A stored the account info shared by node B.
            val shareAccountQuery = nodeA.rpc.vaultQuery(AccountInfo::class.java).states[1]
            println("account info: $shareAccountQuery")
            assertEquals(sharingAccountToA, shareAccountQuery)

            log.info("Creating a confidential identity to the shared account in node B.")
            val accountB1Anonymous = nodeA.rpc.startFlow(::RequestKeyForAccount, createAccountOnB.state.data).returnValue.getOrThrow()
            println("anonymous id: $accountB1Anonymous")

            log.info("node A moves some tokens to the shared account of node B.")
            val moveTokenTx = nodeA.rpc.startFlow(
                    flowConstructor = ::MoveFungibleTokens,
                    arg0 = PartyAndAmount(accountB1Anonymous, 25.GBP),
                    arg1 = emptyList(),
                    arg2 = null,
                    arg3 = nodeA.legalIdentity()    // Change holder is A legal identity not account on A.
            ).returnValue.getOrThrow()
            nodeB.rpc.watchForTransaction(moveTokenTx).getOrThrow()
            log.info("completed move txn")

            val stateFromTxn = moveTokenTx.coreTransaction.outRefsOfType<FungibleToken>()
            println("State From move txn: $stateFromTxn")
            val tokenStateFromA = nodeA.rpc.vaultQueryBy<FungibleToken>().states[0].state
            println("tokenStateFromA: $tokenStateFromA")
            val tokenStateFromB = nodeB.rpc.vaultQueryBy<FungibleToken>().states[0].state
            println("tokenStateFromB: $tokenStateFromB")
            assertEquals(stateFromTxn[0].state, tokenStateFromB)
            assertEquals(stateFromTxn[1].state, tokenStateFromA)
            log.info("completed state check")

            val moveTxHolder = moveTokenTx.coreTransaction.outRefsOfType<FungibleToken>()[0].state.data.holder
            println("moveTxHolder: $moveTxHolder")

            log.info("Sharing key mapping with node C.")
            assertNull(nodeC.rpc.wellKnownPartyFromAnonymous(moveTxHolder))

            nodeA.rpc.startFlow(::SyncKeyMappingInitiator, nodeC.legalIdentity(), moveTokenTx.tx)

            log.info("Sharing key mapping with node C completed.")

            // There should only ever be one SMM removal on node C. The Sync key mapping flow responder.
            nodeC.rpc.stateMachinesFeed().updates.filter { it is StateMachineUpdate.Removed }.toFuture().getOrThrow()

            val partyResolvedByNodeC = nodeC.rpc.wellKnownPartyFromAnonymous(moveTxHolder)
            val partyResolvedByNodeA = nodeA.rpc.wellKnownPartyFromAnonymous(moveTxHolder)
            println("partyResolvedByNodeC: $partyResolvedByNodeC")
            println("partyResolvedByNodeA: $partyResolvedByNodeA")
            assertEquals(nodeB.legalIdentity(), partyResolvedByNodeC)
        }
    }
}