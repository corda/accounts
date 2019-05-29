package net.corda.accounts.cordapp.sweepstake

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.accounts.cordapp.sweepstake.flows.TestUtils.Companion.REQUIRED_CORDAPP_PACKAGES
import net.corda.accounts.cordapp.sweepstake.service.TournamentService
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.flows.ShareStateAndSyncAccountsFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
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
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals

/**
 * Integration test to
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

        val accountsForA = proxyA.startFlow(::GetAccountsFlow, true).returnValue.getOrThrow()
        val accountsForB = proxyB.startFlow(::GetAccountsFlow, false).returnValue.getOrThrow()
        val accountsForC = proxyC.startFlow(::GetAccountsFlow, false).returnValue.getOrThrow()

        require(accountsForB.containsAll(accountsForA))
        require(accountsForC.containsAll(accountsForA))

        // Issue team states
        val listOfIssuedTeamStates = mutableListOf<StateAndRef<TeamState>>()
        val mapPlayerToTeam = accountsForA.zip(teams).toMap().toMutableMap()
        val iterableMap = mapPlayerToTeam.iterator()
        while (iterableMap.hasNext()) {
            val entry = iterableMap.next()
            if (!entry.value.isAssigned) {
                val team = proxyA.startFlow(::IssueTeamWrapper, entry.key, entry.value).returnValue.getOrThrow()
                listOfIssuedTeamStates.add(team)
                mapPlayerToTeam.replace(entry.key, entry.value.copy(isAssigned = true))
            }
        }

        verifyAllTeamsHaveBeenAssignedToPlayers(mapPlayerToTeam)

        // Share the team states and sync the accounts
        for (team in listOfIssuedTeamStates) {
            proxyA.startFlow(::ShareStateAndSyncAccountsFlow, team, nodeC.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            proxyA.startFlow(::ShareStateAndSyncAccountsFlow, team, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }

        // Assign accounts to groups
        proxyA.startFlow(::AssignAccountsToGroups, accountsForA, teams.size, proxyC.nodeInfo().singleIdentity()).returnValue.getOrThrow()

        val groups = proxyA.startFlow(::VerifyGroups).returnValue.getOrThrow()

        groups.forEach {
            println(it.state.data.groupName)
            it.state.data.accounts.forEach { println(it) }
        }

        // Run the match simulations


        // Run distribute winnings flow

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
                    extraCordappPackagesToScan = REQUIRED_CORDAPP_PACKAGES)
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