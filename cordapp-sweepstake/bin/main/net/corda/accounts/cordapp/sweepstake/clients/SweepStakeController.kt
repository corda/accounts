package net.corda.accounts.cordapp.sweepstake.clients

import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.core.utilities.contextLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class SweepStakeController(@Autowired private val rpc: NodeRPCConnection) {

    companion object {
        private val logger = contextLogger()
    }

    private val proxy = rpc.proxy

    @RequestMapping("/load-players/", method = [RequestMethod.GET])
    fun loadPLayers(): List<Participant> {
        return createParticipantsForTournament()
    }

    @RequestMapping("/issue-teams/", method = [RequestMethod.GET])
    fun issueTeams(): List<Participant> {
        val teams = createTeamsForTournament()
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