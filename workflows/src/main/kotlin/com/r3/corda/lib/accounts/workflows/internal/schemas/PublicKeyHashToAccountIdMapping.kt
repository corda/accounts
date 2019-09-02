package com.r3.corda.lib.accounts.workflows.internal.schemas

import net.corda.core.crypto.toStringShort
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import org.apache.commons.lang3.ArrayUtils
import org.hibernate.annotations.Type
import java.security.PublicKey
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "public_key_to_account_id", indexes = [
    Index(name = "external_id_idx", columnList = "external_id"),
    Index(name = "public_key_hash_idx", columnList = "public_key_hash")
])
data class PublicKeyHashToAccountIdMapping(
        @Id
        @Column(name = "public_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
        var publicKeyHash: String,

        @Lob
        @Column(name = "public_key", nullable = false)
        var publicKey: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY,

        @Column(name = "external_id", nullable = false)
        @Type(type = "uuid-char")
        val externalId: UUID
) {
    constructor(publicKey: PublicKey, externalId: UUID) : this(publicKey.toStringShort(), publicKey.encoded, externalId)
}