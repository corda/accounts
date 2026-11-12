package com.r3.corda.lib.accounts.contracts.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.UniqueConstraint
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable
import jakarta.persistence.UniqueConstraint as JakartaUniqueConstraint

object AccountsContractsSchema

object AccountsContractsSchemaV1 : MappedSchema(
        schemaFamily = AccountsContractsSchema::class.java,
        version = 1,
        mappedTypes = listOf(PersistentAccountInfo::class.java)
)

/**
 * The entity carries both the `javax.persistence` (Corda 4.14 and earlier, Hibernate 5) and the `jakarta.persistence` (Corda 4.15,
 * Hibernate 7) annotations, with identical attributes, so that one jar runs on both: Hibernate reads the set it understands and the JVM
 * drops the annotations whose classes are not on the node's classpath. Neither API jar is bundled with the CorDapp.
 */
@Entity
@JakartaEntity
@Table(name = "accounts", uniqueConstraints = [
    UniqueConstraint(name = "id_constraint", columnNames = ["identifier"]),
    UniqueConstraint(name = "host_and_name_constraint", columnNames = ["host", "name"])
], indexes = [
    Index(name = "accountId_idx", columnList = "identifier"),
    Index(name = "accountHost_idx", columnList = "host"),
    Index(name = "name_idx", columnList = "name")
])
@JakartaTable(name = "accounts", uniqueConstraints = [
    JakartaUniqueConstraint(name = "id_constraint", columnNames = ["identifier"]),
    JakartaUniqueConstraint(name = "host_and_name_constraint", columnNames = ["host", "name"])
], indexes = [
    JakartaIndex(name = "accountId_idx", columnList = "identifier"),
    JakartaIndex(name = "accountHost_idx", columnList = "host"),
    JakartaIndex(name = "name_idx", columnList = "name")
])
data class PersistentAccountInfo(
        @Column(name = "identifier", unique = true, nullable = false)
        @JakartaColumn(name = "identifier", unique = true, nullable = false)
        val id: UUID,
        @Column(name = "name", unique = false, nullable = false)
        @JakartaColumn(name = "name", unique = false, nullable = false)
        val name: String,
        @Column(name = "host", unique = false, nullable = false)
        @JakartaColumn(name = "host", unique = false, nullable = false)
        val host: Party
) : PersistentState()
