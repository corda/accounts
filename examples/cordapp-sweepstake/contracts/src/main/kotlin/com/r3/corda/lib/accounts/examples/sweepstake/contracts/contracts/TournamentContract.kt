package com.r3.corda.lib.accounts.examples.sweepstake.contracts.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class TournamentContract : Contract {

    companion object {
        val ISSUE_TEAM: TournamentCommands = TournamentCommands("ISSUE_TEAM")
        val MATCH_WON: TournamentCommands = TournamentCommands("MATCH_WON")
        val ISSUE_GROUP: TournamentCommands = TournamentCommands("ISSUE_GROUP")
        val UPDATE_GROUP: TournamentCommands = TournamentCommands("UPDATE_GROUP")
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand(TournamentCommands::class.java)
        when {
            command.value == ISSUE_TEAM -> {
                require(tx.inputs.isEmpty()) { "When assigning teams there cannot be any input states" }
                require(tx.outputStates.size == 1) { "When assigning teams there can only be one output state" }
            }

            command.value == MATCH_WON -> {
                require(tx.inputStates.size == 2) { "There must be two teams in the input state on a match day" }
                require(tx.outputStates.size == 1) { "There can only be one winner in the output state on a match day" }
            }
            command.value == ISSUE_GROUP -> {
                require(tx.inputStates.isEmpty())
                require(tx.outputStates.size == 1)
            }
            command.value == UPDATE_GROUP -> {
                require(tx.inputStates.size == 1)
                require(tx.outputStates.size == 1)
            }
        }
    }
}

data class TournamentCommands(val id: String) : CommandData