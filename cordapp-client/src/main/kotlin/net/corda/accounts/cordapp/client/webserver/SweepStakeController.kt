package net.corda.accounts.cordapp.client.webserver

import net.corda.accounts.cordapp.sweepstake.flows.Participant
import net.corda.accounts.cordapp.sweepstake.flows.generateParticipantsFromFile
import net.corda.accounts.cordapp.sweepstake.flows.generateTeamsFromFile
import net.corda.core.utilities.contextLogger
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sweepstake/")
class SweepStakeController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = contextLogger()
    }

    private val proxy = rpc.proxy

    @RequestMapping("/load-players/", method = [RequestMethod.GET])
    fun createUser(): List<Participant> {
        return generateParticipantsFromFile("src/test/resources/participants.txt")
    }

//    @RequestMapping("/create-accounts/", method = [RequestMethod.GET])
//    fun createAccounts(): List<Participant> {
//        // Create all the accounts for the players on node A
//        val iterablePlayers = players.listIterator()
//        while (iterablePlayers.hasNext()) {
//            val player = iterablePlayers.next()
//            if (!player.hasAccount) {
//                proxyA.startFlow(net.corda.accounts.cordapp.sweepstake.flows::CreateAccountForPlayer, player).returnValue.getOrThrow()
//                iterablePlayers.set(player.copy(hasAccount = true))
//            }
//        }
//
//        return generateParticipantsFromFile("src/test/resources/participants.txt")
//    }

    @RequestMapping("/issue-teams/", method = [RequestMethod.GET])
    fun issueTeams(): List<Participant> {
        val teams = generateTeamsFromFile("src/test/resources/worldcupteams.txt")
        return generateParticipantsFromFile("src/test/resources/participants.txt")
    }

    @RequestMapping("/issue-groups/", method = [RequestMethod.GET])
    fun assignGroups(): List<Participant> {
        return generateParticipantsFromFile("src/test/resources/participants.txt")
    }

    @RequestMapping("/init-tournament/", method = [RequestMethod.GET])
    fun initialiseTournament(): List<Participant> {
        return generateParticipantsFromFile("src/test/resources/participants.txt")
    }
}