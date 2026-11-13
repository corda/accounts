package com.r3.corda.lib.accounts.workflows.internal.schemas

import net.corda.core.schemas.DirectStatePersistable
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Embedded as JakartaEmbedded
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.GeneratedValue as JakartaGeneratedValue
import jakarta.persistence.GenerationType as JakartaGenerationType
import jakarta.persistence.Id as JakartaId
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable

object AccountsWorkflowsSchema

object AccountsWorkflowsSchemaV1 : MappedSchema(
        schemaFamily = AccountsWorkflowsSchema::class.java,
        version = 1,
        mappedTypes = listOf(AllowedToSeeStateMapping::class.java)
)

/**
 * The entity carries both the `javax.persistence` (Corda 4.14 and earlier, Hibernate 5) and the `jakarta.persistence` (Corda 4.15,
 * Hibernate 7) annotations, with identical attributes, so that one jar runs on both: Hibernate reads the set it understands and the JVM
 * drops the annotations whose classes are not on the node's classpath. Neither API jar is bundled with the CorDapp.
 */
@Entity
@JakartaEntity
@Table(name = "account_to_state_refs", indexes = [Index(name = "external_id_idx", columnList = "external_id")])
@JakartaTable(name = "account_to_state_refs", indexes = [JakartaIndex(name = "external_id_idx", columnList = "external_id")])
data class AllowedToSeeStateMapping(
        @Id
        @JakartaId
        @GeneratedValue(strategy = GenerationType.AUTO)
        @JakartaGeneratedValue(strategy = JakartaGenerationType.AUTO)
        var id: Long?,

        @Column(name = "external_id", unique = false, nullable = false)
        @JakartaColumn(name = "external_id", unique = false, nullable = false)
        var externalId: UUID,

        @Embedded
        @JakartaEmbedded
        override var stateRef: PersistentStateRef
) : DirectStatePersistable
