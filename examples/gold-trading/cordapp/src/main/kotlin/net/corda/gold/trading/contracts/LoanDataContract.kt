package net.corda.gold.trading.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.gold.trading.contracts.commands.LoanCommands

class LoanDataContract : Contract {
    companion object {
        val ISSUE: LoanCommands = LoanCommands("ISSUE")
    }

    override fun verify(tx: LedgerTransaction) {
        TODO("not implemented")
    }
}