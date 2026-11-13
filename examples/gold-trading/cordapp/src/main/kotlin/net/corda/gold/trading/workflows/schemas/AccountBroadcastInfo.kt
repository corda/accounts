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
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Convert as JakartaConvert
import jakarta.persistence.ElementCollection as JakartaElementCollection
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.FetchType as JakartaFetchType
import jakarta.persistence.Id as JakartaId
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable

/**
 * The entity carries both the `javax.persistence` (Corda 4.14 and earlier, Hibernate 5) and the `jakarta.persistence` (Corda 4.15,
 * Hibernate 7) annotations, with identical attributes, so that one jar runs on both.
 */
@Entity
@JakartaEntity
@Table(name = "account_broadcast", indexes = [Index(name = "account_pk_idx", columnList = "account_uuid")])
@JakartaTable(name = "account_broadcast", indexes = [JakartaIndex(name = "account_pk_idx", columnList = "account_uuid")])
@CordaSerializable
data class AccountBroadcastInfo(

        // The id keeps Hibernate's default UUID mapping: Hibernate 7 does not allow an AttributeConverter on an @Id attribute.
        @Id
        @JakartaId
        @Column(name = "account_uuid", unique = true, nullable = false)
        @JakartaColumn(name = "account_uuid", unique = true, nullable = false)
        var accountUUID: UUID?,

        @Column(name = "broadcast_accounts", nullable = false)
        @JakartaColumn(name = "broadcast_accounts", nullable = false)
        @ElementCollection(fetch = FetchType.EAGER)
        @JakartaElementCollection(fetch = JakartaFetchType.EAGER)
        @Convert(converter = UUIDConverter::class)
        @JakartaConvert(converter = JakartaUUIDConverter::class)
        var broadcastAccounts: List<UUID>?


) : MappedSchema(AccountBroadcastInfo::class.java, 1, listOf(AccountBroadcastInfo::class.java)) {
    constructor() : this(null, null)
}
