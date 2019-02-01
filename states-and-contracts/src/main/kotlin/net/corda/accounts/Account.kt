package net.corda.accounts

import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "KNOWN_ACCOUNTS")
data class Account(
    @Id
    @Column(name = "id", unique = true, nullable = false)
    val id: OpaqueBytes?,
    @Column(name = "key")
    val signingKey: PublicKey?,
    @Column(name = "hoster")
    val knownHoster: Party?
)