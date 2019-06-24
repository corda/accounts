package com.r3.corda.lib.accounts.cordapp.sweepstake.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import com.r3.corda.lib.accounts.cordapp.sweepstake.service.TournamentService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DistributeWinningsTest {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = InternalMockNetwork(
                cordappPackages = TestUtils.REQUIRED_CORDAPP_PACKAGES,
                networkSendManuallyPumped = false,
                threadPerNode = true,
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        )
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }


    @Test
    fun `distribute winnings between groups`() {

        val aliceService = aliceNode.services.cordaService(KeyManagementBackedAccountService::class.java)

        createAccountsForNode(aliceService)

        val accounts = aliceService.ourAccounts()

        // Issue the teams
        accounts.zip(TestUtils.teams).forEach {
            bobNode.services.startFlow(IssueTeamWrapper(it.first, it.second)).also {
                it.resultFuture.getOrThrow()
            }
        }

        aliceService.services.cordaService(TournamentService::class.java).assignAccountsToGroups(accounts, 8, bobNode.info.singleIdentity())

        val tournamentService = aliceNode.services.cordaService(TournamentService::class.java)

        // Mock up two winning teams
        val winners = tournamentService.getTeamStates().take(2)

        val winningParties = aliceNode.services.startFlow(DistributeWinningsFlow(winners, 200L, GBP)).also {
            it.resultFuture.getOrThrow()
        }.resultFuture.getOrThrow()

        val issuerCriteria = tokenAmountWithIssuerCriteria(GBP, aliceNode.services.myInfo.legalIdentities.first())
        val tokens = aliceNode.services.vaultService.queryBy<FungibleToken<*>>(issuerCriteria).states
        Assert.assertThat(tokens.size, `is`(IsEqual.equalTo(winningParties.size)))

        val winningQuantity = 200L/(winningParties.size) * 100
        tokens.forEach {
                Assert.assertThat(it.state.data.amount.quantity, `is`(IsEqual.equalTo(winningQuantity)))
        }
    }
}