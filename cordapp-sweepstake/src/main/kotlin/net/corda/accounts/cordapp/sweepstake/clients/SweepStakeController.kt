package net.corda.accounts.cordapp.sweepstake.clients

import net.corda.accounts.cordapp.sweepstake.flows.*
import net.corda.accounts.cordapp.sweepstake.states.AccountGroup
import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class SweepStakeController(@Autowired private val rpc: NodeRPCConnection) {

    private val proxy = rpc.proxy

    @RequestMapping("/load-players/", method = [RequestMethod.GET])
    fun loadPLayers(): List<Participant> {
        return createParticipantsForTournament()
    }

    @RequestMapping("/create-accounts-issue-teams/", method = [RequestMethod.GET])
    fun createAccountsAndIssueTeams(): Map<String, String> {
        val accountsFromVault = proxy.startFlow(::GetAccountsFlow, true).returnValue.getOrThrow()

        val participants = createParticipantsForTournament()
        var accounts = mutableListOf<StateAndRef<AccountInfo>>()

        if(accountsFromVault.isEmpty()) {
        participants.forEach {
           val newAccount =  proxy.startFlow(::CreateAccountForPlayer, it).returnValue.getOrThrow()
            accounts.add(newAccount)
            }
        } else {
            accountsFromVault.forEach {
                accounts.add(it)
            }
        }

        val teams = createTeamsForTournament()

        val accountIdToTeamName = accounts.zip(teams).toMap()

        val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
        if (teamStates.isEmpty()) {
            accountIdToTeamName.forEach {
                proxy.startFlow(::IssueTeamWrapper, it.key, it.value).returnValue.getOrThrow()
            }
        }
        return accountIdToTeamName.mapKeys { it.key.state.data.accountId.toString() }.mapValues { it.value.teamName }
    }

    @RequestMapping("/issue-groups/", method = [RequestMethod.GET])
    fun assignGroups() {
        val groups = proxy.startFlow(::GetAccountGroupInfo).returnValue.getOrThrow()
        if (groups.isEmpty()) {
            val teamStates = proxy.startFlow(::GetTeamStates).returnValue.getOrThrow()
            val accountsFromVault = proxy.startFlow(::GetAccountsFlow, true).returnValue.getOrThrow()
            proxy.startFlow(::AssignAccountsToGroups, accountsFromVault, teamStates.size)
        }
    }

    @RequestMapping("/init-tournament/", method = [RequestMethod.GET])
    fun initialiseTournament(): List<Participant> {
        return generateParticipantsFromFile("src/test/resources/participants.txt")
    }
}