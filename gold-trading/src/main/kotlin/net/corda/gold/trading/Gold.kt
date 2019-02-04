package net.corda.gold.trading

import net.corda.accounts.service.AccountInfo
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@BelongsToContract(GoldBrickContract::class)
data class GoldBrick(val owningAccount: AccountInfo? = null) : ContractState {
    override val participants: List<AbstractParty>
        get() =
            if (owningAccount == null) {
                listOf()
            } else {
                listOf(owningAccount.accountHost)
            }

}


data class GoldCommander(val id: String) : CommandData


class GoldBrickContract : Contract {
    companion object {
        val MINE: GoldCommander = GoldCommander("MINE")
        val TRANSFER_TO_ACCOUNT: GoldCommander =
            GoldCommander("ACCOUNT_TRANSFER")
        val TRANSFER_TO_HOLDER: GoldCommander =
            GoldCommander("HOLDER_TRANSFER")
        val MELT: GoldCommander = GoldCommander("MELT")
    }

    override fun verify(tx: LedgerTransaction) {


        val goldCommand = tx.commands.requireSingleCommand(GoldCommander::class.java)

        when {

            goldCommand.value == MINE -> {
                require(tx.inputs.isEmpty()) { "When mining there cannot be any inputs" }
            }

            goldCommand.value == TRANSFER_TO_ACCOUNT -> {
                val accountData = tx.referenceInputRefsOfType(AccountInfo::class.java).single()
                val outputGold = tx.outputsOfType(GoldBrick::class.java).single()
                val inputGold = tx.inputsOfType(GoldBrick::class.java).single()
                require(accountData.state.data.signingKey in goldCommand.signers){"The account that is receiving the gold must be a required signer"}
                require(inputGold.owningAccount?.let { it.signingKey !in goldCommand.signers  }?: true){"The account that is sending the gold must be a required signer"}
                require(accountData.state.data == outputGold.owningAccount) {"The account to transfer to must equal the account referenced in the transaction"}

            }
            goldCommand.value == TRANSFER_TO_HOLDER -> {
            }
            else -> require(tx.outputs.isEmpty()) { "When melting there cannot be any outputs" }
        }

    }
}