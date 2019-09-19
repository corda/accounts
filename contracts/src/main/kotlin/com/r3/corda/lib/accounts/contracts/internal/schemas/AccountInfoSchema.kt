package com.r3.corda.lib.accounts.contracts.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.*

object AccountSchema : MappedSchema(
        PersistentAccountInfo::class.java,
        version = 1,
        mappedTypes = listOf(PersistentAccountInfo::class.java)
)

@Entity
@Table(name = "accounts", uniqueConstraints = [
    UniqueConstraint(name = "id_constraint", columnNames = ["identifier"]),
    UniqueConstraint(name = "host_and_name_constraint", columnNames = ["host", "name"])
], indexes = [
    Index(name = "accountId_idx", columnList = "identifier"),
    Index(name = "accountHost_idx", columnList = "host"),
    Index(name = "name_idx", columnList = "name")
])
data class PersistentAccountInfo(
        @Column(name = "identifier", unique = true, nullable = false)
        val id: UUID,
        @Column(name = "name", unique = false, nullable = false)
        val name: String,
        @Column(name = "host", unique = false, nullable = false)
        val host: Party
) : PersistentState()