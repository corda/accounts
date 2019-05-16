package net.corda.accounts.states

import net.corda.accounts.contracts.IdentityStateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import java.util.*

@BelongsToContract(IdentityStateContract::class)
data class IdentityState(val name: String,
                         val party: Party,
                         val accounts: List<UUID>) : ContractState {
    override val participants get() = listOf(party)
}