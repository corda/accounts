package net.corda.accounts.cordapp.sweepstake.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class TournamentContract : Contract {

    companion object {
        val ISSUE: TournamentCommands = TournamentCommands("ASSIGN_TEAM")
        val MATCH_WON: TournamentCommands = TournamentCommands("MATCH_WON")
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand(TournamentCommands::class.java)
        when {
            command.value == ISSUE -> {
                require(tx.inputs.isEmpty()) { "When assigning teams there cannot be any input states" }
                require(tx.outputStates.size == 1 ) { "When assigning teams there can only be one output state" }
            }

            command.value == MATCH_WON -> {
                require(tx.inputStates.size == 2) { "There must be two teams in the input state on a match day" }
                require(tx.outputStates.size == 1) { "There can only be one winner in the output state on a match day" }
            }
        }
    }
}

data class TournamentCommands(val id: String) : CommandData