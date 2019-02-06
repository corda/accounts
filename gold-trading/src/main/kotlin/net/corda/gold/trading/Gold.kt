package net.corda.gold.trading

import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

@BelongsToContract(LoanBookContract::class)
data class LoanBook(val owningAccount: PublicKey?) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOfNotNull(owningAccount).map { AnonymousParty(it) }
}


data class LoanCommands(val id: String) : CommandData


class LoanBookContract : Contract {
    companion object {
        val MINE: LoanCommands = LoanCommands("MINE")
        val TRANSFER_TO_ACCOUNT: LoanCommands = LoanCommands("ACCOUNT_TRANSFER")
        val TRANSFER_TO_HOLDER: LoanCommands = LoanCommands("HOLDER_TRANSFER")
        val MELT: LoanCommands = LoanCommands("MELT")
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand(LoanCommands::class.java)

        when {

            command.value == MINE -> {
                require(tx.inputs.isEmpty()) { "When mining there cannot be any inputs" }
            }

            command.value == TRANSFER_TO_ACCOUNT -> {
                val attachedAccounts = tx.referenceInputRefsOfType(AccountInfo::class.java)
                val outputGold = tx.outputsOfType(LoanBook::class.java).single()
                val inputGold = tx.inputsOfType(LoanBook::class.java).single()

                val accountForInputState = attachedAccounts.filter { it.state.data.signingKey == inputGold.owningAccount }.singleOrNull()?.state?.data
                val accountForOutputState = attachedAccounts.filter { it.state.data.signingKey == outputGold.owningAccount }.singleOrNull()?.state?.data

                requireNotNull(accountForInputState) { "The account info state for the current owner must be attached to the transaction" }
                requireNotNull(accountForOutputState) { "The account info state for the new owner must be attached to the transaction" }


                require(accountForInputState?.signingKey in command.signers) { "The account that is selling the loan must be a required signer" }
                require(accountForOutputState?.signingKey in command.signers) { "The account that is buying the loan must be a required signer" }

                require(accountForOutputState?.accountHost?.owningKey in command.signers) { "The hosting party for the account that is receiving the loan must be a required signer" }
                require(accountForInputState?.accountHost?.owningKey in command.signers) { "The hosting party for the account that is sending the loan must be a required signer" }

            }
            command.value == TRANSFER_TO_HOLDER -> {
            }
            else -> require(tx.outputs.isEmpty()) { "When melting there cannot be any outputs" }
        }

    }
}