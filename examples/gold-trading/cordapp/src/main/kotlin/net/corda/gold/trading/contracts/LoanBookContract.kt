package net.corda.gold.trading.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction
import net.corda.gold.trading.contracts.commands.LoanCommands
import net.corda.gold.trading.contracts.states.LoanBook

class LoanBookContract : Contract {

    companion object {
        val ISSUE: LoanCommands = LoanCommands("MINE")
        val TRANSFER_TO_ACCOUNT: LoanCommands = LoanCommands("ACCOUNT_TRANSFER")
        val TRANSFER_TO_HOLDER: LoanCommands = LoanCommands("HOLDER_TRANSFER")
        val SPLIT: LoanCommands = LoanCommands("SPLIT")
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand(LoanCommands::class.java)

        when {

            command.value == ISSUE -> {
                require(tx.inputs.isEmpty()) { "When mining there cannot be any inputs" }
            }

            command.value == TRANSFER_TO_ACCOUNT -> {
                val outputGold = tx.outputsOfType(LoanBook::class.java).single()
                val inputGold = tx.inputsOfType(LoanBook::class.java).single()
                require(outputGold.owningAccount in command.signers) { "The account receiving the loan must be a required signer" }
                inputGold.owningAccount?.let {
                    require(inputGold.owningAccount in command.signers) { "The account sending the loan must be a required signer" }
                }
            }
            command.value == TRANSFER_TO_HOLDER -> {
            }

            command.value == SPLIT -> {
                val inputGold = tx.inputsOfType(LoanBook::class.java).single()
                val outputGolds = tx.outRefsOfType(LoanBook::class.java)

                require(outputGolds.sumOf { it.state.data.valueInUSD } == inputGold.valueInUSD) { "Value of split coins must be equal to original value" }
                for (outputGold in outputGolds) {
                    require(inputGold.dealId == outputGold.state.data.dealId) { "Loans can only be split whilst maintaining original deal" }
                }

            }


            else -> {
                throw IllegalStateException("Invalid command for contract ${LoanBookContract::class.java.canonicalName}")
            }
        }
    }
}




