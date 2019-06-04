package net.corda.accounts.states

import net.corda.accounts.contracts.AccountInfoContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.util.*
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.Table

@BelongsToContract(AccountInfoContract::class)
@Table(name = "ACCOUNTS")
@CordaSerializable
data class AccountInfo(
        @Column(name = "name", unique = false, nullable = false)
        val accountName: String,
        @Column(name = "host", unique = false, nullable = false)
        val accountHost: Party,
        @Id
        @Column(name = "id", unique = true, nullable = false)
        val accountId: UUID,
        override val linearId: UniqueIdentifier = UniqueIdentifier(accountName, accountId),
        @Column(name = "status")
        val status: AccountStatus = AccountStatus.ACTIVE
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(accountHost)
}

object AccountSchema : MappedSchema(AccountInfo::class.java, version = 1, mappedTypes = listOf(AccountInfo::class.java))

@CordaSerializable
enum class AccountStatus {
    ACTIVE,
    INACTIVE
}
