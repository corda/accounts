package com.r3.corda.lib.accounts.examples.sweepstake.contracts.states

import com.r3.corda.lib.accounts.examples.sweepstake.contracts.contracts.TournamentContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

@BelongsToContract(TournamentContract::class)
data class TeamState(val team: WorldCupTeam,
                     val assignedToPlayer: Boolean? = false,
                     val owningKey: PublicKey? = null,
                     val isStillPlaying: Boolean? = false,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<AbstractParty>
        get() = listOfNotNull(owningKey).map { AnonymousParty(it) }
}

@CordaSerializable
data class WorldCupTeam(val teamName: String, val isAssigned: Boolean)