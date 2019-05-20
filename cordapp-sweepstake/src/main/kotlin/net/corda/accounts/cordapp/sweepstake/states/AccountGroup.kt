package net.corda.accounts.cordapp.sweepstake.states

import net.corda.accounts.cordapp.sweepstake.contracts.AccountGroupContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import java.util.*

@BelongsToContract(AccountGroupContract::class)
data class AccountGroup(val name: String,
                        val party: Party,
                        val accounts: List<UUID>) : ContractState {
    override val participants get() = listOf(party)
}