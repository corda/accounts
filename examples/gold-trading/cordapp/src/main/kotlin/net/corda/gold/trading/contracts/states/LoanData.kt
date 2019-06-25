package net.corda.gold.trading.contracts.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.toSHA256Bytes

data class LoanData(
        val loanValue: Long,
        val loanInterestRate: Double,
        val issuerRefName: String,
        val issuer: AbstractParty,
        override val linearId: UniqueIdentifier = UniqueIdentifier(issuerRefName + " issued by " + issuer.owningKey.toSHA256Bytes())
) : LinearState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)
}