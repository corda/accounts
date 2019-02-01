package net.corda.accounts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

data class GoldBrick(val owningAccount: Account) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf()
}


data class GoldCommander(val id: String) : CommandData


class GoldBrickContract : Contract {
    companion object {
        val MINE: GoldCommander = GoldCommander("MINE")
        val TRANSFER_TO_ACCOUNT: GoldCommander = GoldCommander("ACCOUNT_TRANSFER")
        val TRANSFER_TO_HOLDER: GoldCommander = GoldCommander("HOLDER_TRANSFER")
        val MELT: GoldCommander = GoldCommander("MELT")
    }

    override fun verify(tx: LedgerTransaction) {


        val goldCommand = tx.commands.requireSingleCommand(GoldCommander::class.java)

        when {
            goldCommand.value == MINE -> {
                require(tx.inputs.isEmpty()) { "When mining there cannot be any inputs" }
            }
            goldCommand.value == TRANSFER_TO_ACCOUNT -> {
            }
            goldCommand.value == TRANSFER_TO_HOLDER -> {
            }
            else -> require(tx.outputs.isEmpty()) { "When melting there cannot be any outputs" }
        }

    }
}