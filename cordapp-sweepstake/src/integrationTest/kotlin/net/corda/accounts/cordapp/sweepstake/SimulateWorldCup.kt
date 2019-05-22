package net.corda.accounts.cordapp.sweepstake

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.cordapp.sweepstake.flows.IssueTeamWrapper
import net.corda.accounts.cordapp.sweepstake.flows.Participant
import net.corda.accounts.cordapp.sweepstake.flows.Utils.Companion.REQUIRED_CORDAPP_PACKAGES
import net.corda.accounts.cordapp.sweepstake.flows.generateParticipantsFromFile
import net.corda.accounts.cordapp.sweepstake.flows.generateTeamsFromFile
import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals


class SimulateWorldCup {

    private val partyA = TestIdentity(ALICE_NAME)
    private val partyB = TestIdentity(BOB_NAME)
    private val partyC = TestIdentity(CHARLIE_NAME)

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

        val playersAndTeams = accountsForB.zip(teams).toMap().toMutableMap()

        val iterableMap = playersAndTeams.iterator()
        while(iterableMap.hasNext()) {
            val entry = iterableMap.next()
            if (!entry.value.isAssigned) {
                proxyB.startFlow(::IssueTeamWrapper, entry.key, entry.value).returnValue.getOrThrow()
                playersAndTeams.remove(entry.key)
                playersAndTeams.putIfAbsent(entry.key, entry.value.copy(isAssigned = true))
            }
        }
        playersAndTeams.forEach {
          e -> println(e.value.isAssigned)
        }
        // Issue team states

        // Assign accounts to groups

        // Run the match simulations

        // Run distribute winnings flow

    }

    private fun verifyAllPlayersHaveBeenAssignedAccount(players: MutableList<Participant>) {
        players.forEach { p ->
            require(p.hasAccount) { "Player ${p.playerName} has not been assigned an account." }
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

@StartableByRPC
@InitiatingFlow
internal class CreateAccountForPlayer(private val player: Participant) : FlowLogic<StateAndRef<AccountInfo>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.createAccount(player.playerName).getOrThrow()
    }
}

@StartableByRPC
@InitiatingFlow
internal class ShareAccountInfo(private val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accounts = accountService.allAccounts()
        accounts.forEach {
            account ->
            accountService.shareAccountInfoWithParty(account.state.data.accountId, otherParty).getOrThrow()
        }
    }
}