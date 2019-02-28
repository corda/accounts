package net.corda.gold.trading

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@StartableByRPC
@InitiatingFlow
class ShareLoanDataWithAccountFlow() {
}


@Entity
@Table(name = "account_to_dealstate", indexes = [Index(name = "pk_hash_to_xid_idx", columnList = "external_id")])
class AllowedToSeeStateMapping(
    @GeneratedValue
    @Column(name = "state_ref", unique = false, nullable = false)
    @Convert(converter = StateRefToTextConverter::class)    // when explicitly stating which converter to use don't annotate the converter with
    val stateRef: StateRef?,

    @Column(name = "external_id", nullable = false)
    @Type(type = "uuid-char")
    val externalId: UUID?
)

/**
 * Converts to and from a StateRef into a string.
 * Used by JPA to automatically map a StateRef to a text column
 *
 * see https://stackoverflow.com/questions/45475265/corda-error-org-hibernate-instantiationexception-no-default-constructor-for-en for the gradle changes required to use this
 */

@Converter
class StateRefToTextConverter : AttributeConverter<StateRef, String> {
    val SEPERATOR: String = "|"

    override fun convertToDatabaseColumn(stateRef: StateRef?): String? = stateRef?.txhash.toString().plus(SEPERATOR).plus(stateRef?.index.toString())

    override fun convertToEntityAttribute(text: String?): StateRef? {
        val parts = text?.split(SEPERATOR)
        return parts?.let { StateRef(SecureHash.parse(it[0]), it[1].toInt())}

    }
}