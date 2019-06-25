package net.corda.gold.trading.contracts.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.gold.trading.contracts.LoanBookContract
import java.security.PublicKey
import java.util.*

@BelongsToContract(LoanBookContract::class)
data class LoanBook(val dealId: UUID, val valueInUSD: Long, val owningAccount: PublicKey? = null) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOfNotNull(owningAccount).map { AnonymousParty(it) }
}