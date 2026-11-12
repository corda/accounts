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
@Table(name = "web_permissioning", indexes = [Index(name = "web_account_pk_idx", columnList = "web_account_name")])
@CordaSerializable
data class WebAccountPermissioning(

        @Id
        @Column(name = "web_account_name", unique = true, nullable = false)
        var webAccount: String?,

        @Column(name = "permissioned_accounts", nullable = false)
        @ElementCollection(fetch = FetchType.EAGER)
        @Convert(converter = UUIDConverter::class)
        var permissionedAccounts: List<UUID>?


) : MappedSchema(WebAccountPermissioning::class.java, 1, listOf(WebAccountPermissioning::class.java)) {
    constructor() : this(null, null)
}
