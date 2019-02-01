package net.corda.accounts.model

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.toBase58
import net.corda.core.utilities.toBase58String
import java.security.PublicKey
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "KNOWN_ACCOUNTS")
class Account(
    @Id
    @Column(name = "id", unique = true, nullable = false)
    val id: ByteArray?,
    @Column(name = "key")
    val signingKey: PublicKey?,
    @Column(name = "hoster")
    val knownHoster: Party?


) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Account

        if (id != null) {
            if (other.id == null) return false
            if (!id.contentEquals(other.id)) return false
        } else if (other.id != null) return false
        if (signingKey != other.signingKey) return false
        if (knownHoster != other.knownHoster) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.contentHashCode() ?: 0
        result = 31 * result + (signingKey?.hashCode() ?: 0)
        result = 31 * result + (knownHoster?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Account(id=${id?.toBase58()}, signingKey=${signingKey?.toBase58String()}, knownHoster=${knownHoster?.name})"
    }


}


object AccountSchema : MappedSchema(Account::class.java, version = 1, mappedTypes = listOf(Account::class.java))