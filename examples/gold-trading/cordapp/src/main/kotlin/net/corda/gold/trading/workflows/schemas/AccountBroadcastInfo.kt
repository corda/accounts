package net.corda.gold.trading.workflows.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(name = "account_broadcast", indexes = [Index(name = "account_pk_idx", columnList = "account_uuid")])
@CordaSerializable
data class AccountBroadcastInfo(

        // The id keeps Hibernate's default UUID mapping: Hibernate 7 does not allow an AttributeConverter on an @Id attribute.
        @Id
        @Column(name = "account_uuid", unique = true, nullable = false)
        var accountUUID: UUID?,

        @Column(name = "broadcast_accounts", nullable = false)
        @ElementCollection(fetch = FetchType.EAGER)
        @Convert(converter = UUIDConverter::class)
        var broadcastAccounts: List<UUID>?


) : MappedSchema(AccountBroadcastInfo::class.java, 1, listOf(AccountBroadcastInfo::class.java)) {
    constructor() : this(null, null)
}
