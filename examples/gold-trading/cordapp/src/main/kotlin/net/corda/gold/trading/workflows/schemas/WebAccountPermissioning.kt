package net.corda.gold.trading.workflows.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "web_permissioning", indexes = [Index(name = "web_account_pk_idx", columnList = "web_account_name")])
@CordaSerializable
data class WebAccountPermissioning(

        @Id
        @Column(name = "web_account_name", unique = true, nullable = false)
        var webAccount: String?,

        @Column(name = "permissioned_accounts", nullable = false)
        @ElementCollection(fetch = FetchType.EAGER)
        @Type(type = "uuid-char")
        var permissionedAccounts: List<UUID>?


) : MappedSchema(WebAccountPermissioning::class.java, 1, listOf(WebAccountPermissioning::class.java)) {
    constructor() : this(null, null)
}