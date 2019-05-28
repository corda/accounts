package net.corda.accounts.states

import net.corda.accounts.contracts.AccountInfoContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.util.*
import javax.persistence.*

object AccountSchema : MappedSchema(PersistentAccountInfo::class.java, version = 1, mappedTypes = listOf(PersistentAccountInfo::class.java))

@BelongsToContract(AccountInfoContract::class)
data class AccountInfo(
        val accountName: String,
        val accountHost: Party,
        val accountId: UUID,
        override val linearId: UniqueIdentifier = UniqueIdentifier(accountName, accountId),
        val status: AccountStatus = AccountStatus.ACTIVE
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is AccountSchema) {
            return PersistentAccountInfo(
                    accountName,
                    accountHost,
                    accountId,
                    status
            )
        } else {
            throw IllegalStateException("Cannot construct instance of ${this.javaClass} from Schema: $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(AccountSchema)
    }

    override val participants: List<AbstractParty> get() = listOf(accountHost)
}


@Entity
@Table(name = "accounts", uniqueConstraints = [UniqueConstraint(name = "id_constraint", columnNames = ["id"])],
        indexes = [Index(name = "accountId_idx", columnList = "id"), Index(name = "accountHost_idx", columnList = "host"), Index(name = "name_idx", columnList = "name")])
data class PersistentAccountInfo(
        @Column(name = "name", unique = false, nullable = false)
        val accountName: String,
        @Column(name = "host", unique = false, nullable = false)
        val accountHost: Party,
        @Column(name = "id", unique = true, nullable = false)
        val accountId: UUID,
        @Column(name = "status")
        val status: AccountStatus
) : PersistentState()


@CordaSerializable
enum class AccountStatus {
    ACTIVE,
    INACTIVE
}
