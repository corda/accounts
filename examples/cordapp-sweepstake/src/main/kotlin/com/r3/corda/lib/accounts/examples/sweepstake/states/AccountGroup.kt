package com.r3.corda.lib.accounts.examples.sweepstake.states

import com.r3.corda.lib.accounts.examples.sweepstake.contracts.TournamentContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import java.security.PublicKey
import java.util.*

@BelongsToContract(TournamentContract::class)
data class AccountGroup(val groupName: String,
                        val accounts: List<UUID>,
                        val owningKey: PublicKey? = null,
                        val stateId: String? = null,
                        override val linearId: UniqueIdentifier = UniqueIdentifier(stateId)) : LinearState {
    //Empty state constructor
    constructor() : this("", emptyList(), null, null, UniqueIdentifier(null))

    override val participants: List<AbstractParty>
        get() = listOfNotNull(owningKey).map { AnonymousParty(it) }
}