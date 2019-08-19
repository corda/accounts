package com.r3.corda.lib.accounts.examples.sweepstake.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.examples.sweepstake.flows.*
import com.r3.corda.lib.accounts.examples.sweepstake.states.TeamState
import com.r3.corda.lib.accounts.examples.sweepstake.test.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES
import com.r3.corda.lib.accounts.workflows.flows.AllAccounts
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts
import com.r3.corda.lib.tokens.money.GBP
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsEqual
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals

/**
 * Integration test to test the grouping of accounts. The [AccountGroup] state contains a list of accountIds that
 * can be used to group accounts. In this cordapp, we aim to simulate a world cup sweepstake where by a list of
 * participants are each assigned an [AccountInfo]. The accounts are then assigned to an [AccountGroup] that contains
 * four accounts. They are also assigned a team that from the world cup - modelled as a [TeamState]. We then run a series
 * of [MatchDayFlow] whereby two teams are used as input states and one of the teams 'wins' and is used as the output
 * state. This is repeated until there are 4 teams remaining. These final four are shuffled to determine the top 4 spots.
 *
 * The [DistributeWinningsFlow] is then run to determine which accounts are to be issued a proportion of the prize money.
 * For an account that placed in the top four, they will share the prize money with every account in their [AccountGroup].
 */
class SimulateWorldCup {

    @Test
    fun `world cup simulation of a 32 team knockout stage`() = withDriver {
        val aUser = User("aUser", "testPassword1", permissions = setOf(Permissions.all()))
        val bUser = User("bUser", "testPassword2", permissions = setOf(Permissions.all()))
        val cUser = User("cUser", "testPassword3", permissions = setOf(Permissions.all()))

        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aUser)),
                startNode(providedName = BOB_NAME, rpcUsers = listOf(bUser)),
                startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(cUser))
        ).waitForAll()

        val proxyA = createClientProxy(nodeA, aUser)
        val proxyB = createClientProxy(nodeB, bUser)
        val proxyC = createClientProxy(nodeC, cUser)

        verifyNodesResolve(nodeA, nodeB, nodeC)

        // Read list of teams
        val teams = generateTeamsFromFile("src/test/resources/worldcupteams.txt")
        Assertions.assertThat(teams).hasSize(32)

        // Read list of participants
        val players = generateParticipantsFromFile("src/test/resources/participants.txt")
        Assertions.assertThat(players).hasSize(32)

        // Create all the accounts for the players on node A
        val iterablePlayers = players.listIterator()
        while (iterablePlayers.hasNext()) {
            val player = iterablePlayers.next()
            if (!player.hasAccount) {
                proxyA.startFlow(::CreateAccountForPlayer, player).returnValue.getOrThrow()
                iterablePlayers.set(player.copy(hasAccount = true))
            }
        }

        verifyAllPlayersHaveBeenAssignedAccount(players)

        // Share all of the newly created accounts with node B and node C
        proxyA.startFlow(::ShareAccountInfo, proxyB.nodeInfo().legalIdentities.first()).returnValue.getOrThrow()
        proxyA.startFlow(::ShareAccountInfo, proxyC.nodeInfo().legalIdentities.first()).returnValue.getOrThrow()

        val accountsForA = proxyA.startFlow(::OurAccounts).returnValue.getOrThrow()
        val accountsForB = proxyB.startFlow(::AllAccounts).returnValue.getOrThrow()
        val accountsForC = proxyC.startFlow(::AllAccounts).returnValue.getOrThrow()

        require(accountsForB.containsAll(accountsForA))
        require(accountsForC.containsAll(accountsForA))

        // Issue teams to each of the accounts
        val listOfIssuedTeamStates = mutableListOf<StateAndRef<TeamState>>()
        val mapPlayerToTeam = accountsForA.zip(teams).toMap().toMutableMap()
        val iterableMap = mapPlayerToTeam.iterator()
        while (iterableMap.hasNext()) {
            val entry = iterableMap.next()
            if (!entry.value.isAssigned) {
                val team = proxyA.startFlow(::IssueTeamInitiator, entry.key, entry.value).returnValue.getOrThrow()
                listOfIssuedTeamStates.add(team)
                mapPlayerToTeam.replace(entry.key, entry.value.copy(isAssigned = true))
            }
        }

        verifyAllTeamsHaveBeenAssignedToPlayers(mapPlayerToTeam)

        // Share the team states and sync the accounts
        for (team in listOfIssuedTeamStates) {
            proxyA.startFlow(::ShareStateAndSyncAccounts, team, nodeC.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            proxyA.startFlow(::ShareStateAndSyncAccounts, team, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }

        // Assign accounts to groups and share with charlie
        proxyA.startFlow(::AssignAccountsToGroups, accountsForA, teams.size, proxyC.nodeInfo().singleIdentity()).returnValue.getOrThrow()
        val groups = proxyA.startFlow(::GetAccountGroupInfo).returnValue.getOrThrow()

        // Check that each group contains only 4 accounts
        groups.forEach {
            Assert.assertThat(it.state.data.accounts.size, `is`(IsEqual.equalTo(4)))
        }

        groups.forEach {
            proxyA.startFlow(::ShareStateAndSyncAccounts, it, nodeC.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }

        // Run the match flows until there are four teams remaining
        var matchResults = runMatchDayFlows(proxyC, listOfIssuedTeamStates.shuffled())
        while (matchResults.size > 4) {
            matchResults = runMatchDayFlows(proxyC, matchResults)
        }

        // Shuffle the final 4 teams to determine 1st, 2nd, 3rd and 4th place
        val finalResult = matchResults.shuffled()

        // Work out which accounts have won a split of the sweepstake prize
        val winners = proxyA.startFlow(::DistributeWinningsFlow, finalResult, 200L, GBP).returnValue.getOrThrow()

        // Retrieve the parties that have been issued part of the prize from the vault
        val prizeWinners = proxyA.startFlow(::GetPrizeWinners).returnValue.getOrThrow()

        // Confirm winners
        require(prizeWinners.containsAll(winners)) { "The parties that were issued prizes were not returned from the vault." }
    }

    private fun runMatchDayFlows(proxy: CordaRPCOps, teams: List<StateAndRef<TeamState>>): List<StateAndRef<TeamState>> {
        val winners = mutableListOf<StateAndRef<TeamState>>()
        for (i in 1..teams.size step 2) {
            val teamA = teams[i - 1]
            val teamB = teams[i]

            winners.add(proxy.startFlow(::MatchDayFlow, generateQuickWinner(teamA, teamB), teamA, teamB).returnValue.toCompletableFuture().getOrThrow())
        }
        return winners
    }

    private fun verifyAllPlayersHaveBeenAssignedAccount(players: MutableList<Participant>) {
        players.forEach { p ->
            require(p.hasAccount) { "Player ${p.playerName} has not been assigned an account." }
        }
    }

    private fun verifyAllTeamsHaveBeenAssignedToPlayers(map: MutableMap<StateAndRef<AccountInfo>, WorldCupTeam>) {
        map.forEach { e ->
            require(e.value.isAssigned) { "The team ${e.value.teamName} has not been assigned to an account." }
        }
    }


    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
            DriverParameters(
                    isDebug = true,
                    startNodesInProcess = true,
                    cordappsForAllNodes = REQUIRED_CORDAPP_PACKAGES,
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4))
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    private fun createClientProxy(node: NodeHandle, user: User): CordaRPCOps {
        val client = CordaRPCClient(node.rpcAddress)
        return client.start(user.username, user.password).proxy
    }

    private fun verifyNodesResolve(nodeA: NodeHandle, nodeB: NodeHandle, nodeC: NodeHandle) {
        assertEquals(BOB_NAME, nodeA.resolveName(BOB_NAME))
        assertEquals(CHARLIE_NAME, nodeA.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeB.resolveName(ALICE_NAME))
        assertEquals(CHARLIE_NAME, nodeB.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeC.resolveName(ALICE_NAME))
        assertEquals(BOB_NAME, nodeC.resolveName(BOB_NAME))
    }
}