package net.corda.accounts.contracts

import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class AccountInfoContract : Contract {

    data class AccountCommands(private val step: String) : CommandData

    companion object {
        val OPEN = AccountCommands("OPEN")
        val MOVE_HOST = AccountCommands("MOVE_HOST")
    }

    override fun verify(tx: LedgerTransaction) {
        val accountCommand = tx.commands.requireSingleCommand(AccountCommands::class.java)
        if (accountCommand.value == OPEN) {
            require(tx.outputStates.size == 1) { "There should only ever be one output account state" }
            val accountInfo = tx.outputsOfType(AccountInfo::class.java).single()
            val requiredSigners = accountCommand.signers
            require(requiredSigners.size == 1) { "There should only be one required signer for opening an account " }
            require(requiredSigners.single() == accountInfo.accountHost.owningKey) { "Only the hosting node should be able to sign" }
        } else {
            throw NotImplementedError()
        }
    }

}
