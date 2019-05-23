package net.corda.accounts.cordapp.sweepstake.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class TournamentContract : Contract {

    companion object {
        val ISSUE_TEAM: TournamentCommands = TournamentCommands("ISSUE_TEAM")
        val MATCH_WON: TournamentCommands = TournamentCommands("MATCH_WON")
        val ASSIGN_GROUP: TournamentCommands = TournamentCommands("ASSIGN_GROUP")
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand(TournamentCommands::class.java)
        when {
            command.value == ISSUE_TEAM -> {
                require(tx.inputs.isEmpty()) { "When assigning teams there cannot be any input states" }
                require(tx.outputStates.size == 1 ) { "When assigning teams there can only be one output state" }
            }

            command.value == MATCH_WON -> {
                require(tx.inputStates.size == 2) { "There must be two teams in the input state on a match day" }
                require(tx.outputStates.size == 1) { "There can only be one winner in the output state on a match day" }
            }
            command.value == ASSIGN_GROUP -> {
                //TODO
            }
        }
    }
}

data class TournamentCommands(val id: String) : CommandData