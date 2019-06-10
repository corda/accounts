package net.corda.accounts.contracts

import net.corda.accounts.contracts.commands.AccountCommand
import net.corda.accounts.contracts.commands.Open
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class AccountInfoContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val accountCommand = tx.commands.requireSingleCommand(AccountCommand::class.java)
        if (accountCommand.value is Open) {
            require(tx.outputStates.size == 1) { "There should only ever be one output account state." }
            val accountInfo = tx.outputsOfType(AccountInfo::class.java).single()
            val requiredSigners = accountCommand.signers
            require(requiredSigners.size == 1) { "There should only be one required signer for opening an account." }
            require(requiredSigners.single() == accountInfo.host.owningKey) {
                "Only the hosting node should be able to sign."
            }
        } else {
            throw NotImplementedError()
        }
    }
}
