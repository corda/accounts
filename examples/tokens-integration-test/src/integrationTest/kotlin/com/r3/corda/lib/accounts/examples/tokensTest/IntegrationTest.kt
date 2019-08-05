package com.r3.corda.lib.accounts.examples.tokensTest

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.flows.NewKeyForAccount
import com.r3.corda.lib.accounts.workflows.externalIdCriteria
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.*
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test
import kotlin.test.assertEquals

class IntegrationTest {

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

    private val issuer = NodeParameters(
            providedName = CordaX500Name("Issuer", "London", "GB"),
            additionalCordapps = listOf()
    )

    private val nodeParams = listOf(partyA, partyB, issuer)

    private val defaultCorDapps = listOf(
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.examples.flows"), // Contains NewKeyForAccount.
            TestCordapp.findCordapp("com.r3.corda.lib.ci")
    )

    private val driverParameters = DriverParameters(
            startNodesInProcess = false,
            cordappsForAllNodes = defaultCorDapps,
            networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4)
    )

    fun NodeHandle.legalIdentity() = nodeInfo.legalIdentities.single()

    @Test
    fun `node test`() {
        driver(driverParameters) {
            val (A, B, I) = nodeParams.map { params -> startNode(params) }.transpose().getOrThrow()
            log.info("All nodes started up.")

            log.info("Creating two accounts on node A.")
            val createAccountsOnA = listOf(
                    A.rpc.startFlow(::CreateAccount, "PartyA - Roger").returnValue,
                    A.rpc.startFlow(::CreateAccount, "PartyA - Kasia").returnValue
            ).transpose().getOrThrow()

            // Check that A recorded all the new accounts.
            val aAccountsQuery = A.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            assertEquals(createAccountsOnA.toSet(), aAccountsQuery.toSet())

            log.info("Creating two accounts on node B.")
            val createAccountsOnB = listOf(
                    B.rpc.startFlow(::CreateAccount, "PartyB - Stefano").returnValue,
                    B.rpc.startFlow(::CreateAccount, "PartyB - Will").returnValue
            ).transpose().getOrThrow()

            // Check that B recorded all the new accounts.
            val bAccountsQuery = B.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            assertEquals(createAccountsOnB.toSet(), bAccountsQuery.toSet())

            log.info("Sharing account info from node A to node B.")
            val rogerAccount = aAccountsQuery.single { it.state.data.name == "PartyA - Roger" }
            A.rpc.startFlow(::ShareAccountInfo, rogerAccount, listOf(I.legalIdentity())).returnValue.getOrThrow()
            I.rpc.watchForTransaction(rogerAccount.ref.txhash).getOrThrow()
            // Check that B stored the account info.
            val rogerAccountQuery = I.rpc.vaultQuery(AccountInfo::class.java).states.single()
            assertEquals(rogerAccount, rogerAccountQuery)

            log.info("Issuer requesting new key for account on node A.")
            val rogerAnonymousParty = I.rpc.startFlow(::RequestKeyForAccount, rogerAccount.state.data).returnValue.getOrThrow()
            // Check we can resolve the anonymous key to the host node.
            assertEquals(I.rpc.wellKnownPartyFromAnonymous(rogerAnonymousParty), A.legalIdentity())

            log.info("Issuer issuing 100 GBP to account on node A.")
            val tokens = 100 of GBP issuedBy I.legalIdentity() heldBy rogerAnonymousParty
            val issuanceResult = I.rpc.startFlow(
                    ::IssueTokens,
                    tokens,
                    emptyList()
            ).returnValue.getOrThrow()

            A.rpc.watchForTransaction(issuanceResult).getOrThrow()
            // Check that the tokens are assigned to Roger's account on node A.
            val rogerTokensIssueQuery = A.rpc.vaultQueryByCriteria(
                    externalIdCriteria(accountIds = listOf(rogerAccount.state.data.identifier.id)),
                    FungibleState::class.java
            ).states.single()
            assertEquals(tokens, rogerTokensIssueQuery.state.data)

            log.info("Node A moving tokens between accounts.")
            val kasiaAccount = aAccountsQuery.single { it.state.data.name == "PartyA - Kasia" }
            val kasiaAnonymousParty = A.rpc.startFlow(::RequestKeyForAccount, kasiaAccount.state.data).returnValue.getOrThrow()
            // Create a new change key for Roger.
            val newPartyAndCert = A.rpc.startFlow(::NewKeyForAccount, rogerAccount.state.data.identifier.id).returnValue.getOrThrow()
            val newAnonymousParty = newPartyAndCert.party.anonymise()
            // Move tokens.
            val moveTokensTransaction = A.rpc.startFlowDynamic(
                    MoveFungibleTokens::class.java,
                    PartyAndAmount(kasiaAnonymousParty, 50.GBP),
                    emptyList<Party>(),
                    null,
                    newAnonymousParty as AbstractParty
            ).returnValue.getOrThrow()

            A.rpc.watchForTransaction(moveTokensTransaction).getOrThrow()

            log.info(moveTokensTransaction.tx.toString())

            val rogerQuery = A.rpc.vaultQueryByCriteria(externalIdCriteria(listOf(rogerAccount.state.data.identifier.id)), FungibleToken::class.java)
            assertEquals(50.GBP, (rogerQuery.states as List<StateAndRef<FungibleToken<FiatCurrency>>>).sumTokenStateAndRefs().withoutIssuer())

            val kasiaQuery = A.rpc.vaultQueryByCriteria(externalIdCriteria(listOf(kasiaAccount.state.data.identifier.id)), FungibleToken::class.java)
            assertEquals(50.GBP, (kasiaQuery.states as List<StateAndRef<FungibleToken<FiatCurrency>>>).sumTokenStateAndRefs().withoutIssuer())
        }
    }
}

fun CordaRPCOps.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (tx in snapshot) {
        doneFuture(tx)
    } else {
        feed.filter { it.id == tx.id }.toFuture()
    }
}

fun CordaRPCOps.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (txId in snapshot.map { it.id }) {
        doneFuture(snapshot.single { txId == it.id })
    } else {
        feed.filter { it.id == txId }.toFuture()
    }
}