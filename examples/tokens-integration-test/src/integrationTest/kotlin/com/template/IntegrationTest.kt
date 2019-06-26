package com.template

import com.r3.corda.lib.accounts.workflows.externalIdCriteria
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.shell.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.shell.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test
import java.util.concurrent.CompletableFuture

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
            TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
    )

    private val driverParameters = DriverParameters(
            startNodesInProcess = true,
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
            CompletableFuture.allOf(
                    A.rpc.startFlow(::CreateAccount, "PartyA - Roger").returnValue.toCompletableFuture(),
                    A.rpc.startFlow(::CreateAccount, "PartyA - Kasia").returnValue.toCompletableFuture()
            ).getOrThrow()

            val aAccounts = A.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            println(aAccounts)

            log.info("Creating two accounts on node B.")
            CompletableFuture.allOf(
                    B.rpc.startFlow(::CreateAccount, "PartyB - Stefano").returnValue.toCompletableFuture(),
                    B.rpc.startFlow(::CreateAccount, "PartyB - Will").returnValue.toCompletableFuture()
            ).getOrThrow()

            val bAccounts = B.rpc.startFlow(::OurAccounts).returnValue.getOrThrow()
            println(bAccounts)

            log.info("Sharing account info from node A to node B.")
            val rogerAccount = aAccounts.single { it.state.data.name == "PartyA - Roger" }
            A.rpc.startFlow(::ShareAccountInfo, rogerAccount, listOf(I.legalIdentity())).returnValue.getOrThrow()

            log.info("Issuer requesting new key for account on node A.")
            val rogerAnonymousParty = I.rpc.startFlow(::RequestKeyForAccount, rogerAccount.state.data).returnValue.getOrThrow()
            println(rogerAnonymousParty)

            log.info("Issuer issuing 100 GBP to account on node A.")
            val issueTokensTransaction = I.rpc.startFlow(
                    ::IssueTokens,
                    100 of GBP issuedBy I.legalIdentity() heldBy rogerAnonymousParty,
                    emptyList()
            ).returnValue.getOrThrow()
            println(issueTokensTransaction)
            println(issueTokensTransaction.tx)

            log.info("Node A querying for tokens held by account.")
            val rogerAccountCriteria = externalIdCriteria(listOf(rogerAccount.state.data.id.id))
            val newlyIssuedTokens = A.rpc.vaultQueryByCriteria(rogerAccountCriteria, FungibleToken::class.java).states.single()
            println(newlyIssuedTokens)

            log.info("Node A moving tokens between accounts.")
            val kasiaAccount = aAccounts.single { it.state.data.name == "PartyA - Kasia" }
            val kasiaAnonymousParty = A.rpc.startFlow(::RequestKeyForAccount, kasiaAccount.state.data).returnValue.getOrThrow()
            val moveTokensTransaction = A.rpc.startFlowDynamic(
                    MoveFungibleTokens::class.java,
                    PartyAndAmount(kasiaAnonymousParty, 50.GBP),
                    emptyList<Party>(),
                    null,
                    rogerAnonymousParty as AbstractParty?
            ).returnValue.getOrThrow()
            println(moveTokensTransaction)
            println(moveTokensTransaction.tx)

            println("Roger")
            println(A.rpc.vaultQueryByCriteria(externalIdCriteria(listOf(rogerAccount.state.data.id.id)), FungibleToken::class.java).states)
            println("Kasia")
            println(A.rpc.vaultQueryByCriteria(externalIdCriteria(listOf(kasiaAccount.state.data.id.id)), FungibleToken::class.java).states)
        }
    }
}