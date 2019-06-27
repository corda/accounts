package net.corda.gold.trading.contracts.commands

import net.corda.core.contracts.CommandData

data class LoanCommands(val id: String) : CommandData
data class LoanDataCommands(val id: String) : CommandData