package net.corda.accounts.cordapp.sweepstake.clients

import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
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

    @RequestMapping("/create-accounts-issue-teams/", method = [RequestMethod.GET])
    fun createAccountsAndIssueTeams(): Map<StateAndRef<AccountInfo>, WorldCupTeam> {
        val participants = createParticipantsForTournament()
        var accounts = mutableListOf< StateAndRef<AccountInfo>>()
        participants.forEach {
           val newAccount =  proxy.startFlowDynamic(CreateAccountForPlayer::class.java, it).returnValue.getOrThrow()
            accounts.add(newAccount)
        }

        val teams = createTeamsForTournament()
        val accountsToTeams = accounts.zip(teams).toMap()

        accountsToTeams.forEach {
            proxy.startFlowDynamic(IssueTeamWrapper::class.java, it.key, it.value).returnValue.getOrThrow()
        }
        return accountsToTeams
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