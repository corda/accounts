package net.corda.accounts.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.unwrap
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@StartableByRPC
@InitiatingFlow
class ShareStateWithAccountFlow<T : ContractState>(val accountInfo: AccountInfo, val state: StateAndRef<T>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
        val session = initiateFlow(accountInfo.accountHost)
        subFlow(SendTransactionFlow(session, transaction!!))
        session.send(state.ref)
        session.send(accountInfo)
    }
}

@InitiatedBy(ShareStateWithAccountFlow::class)
class ReceiveStateForAccountFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
        val stateToPermission = otherSession.receive<StateRef>().unwrap { it }
        val accountInfo = otherSession.receive<AccountInfo>().unwrap { it }
        serviceHub.withEntityManager {
            val existingEntry = find(AllowedToSeeStateMapping::class.java, accountInfo.accountId) ?: AllowedToSeeStateMapping(accountInfo.accountId, listOf())
            val newEntry = existingEntry.copy(stateRef = existingEntry.stateRef!! + listOf(stateToPermission))
            persist(newEntry)
        }
    }

}


@Entity
@Table(name = "account_to_state_refs", indexes = [Index(name = "external_id_pk_idx", columnList = "external_id")])
data class AllowedToSeeStateMapping(

    @Id
    @Column(name = "external_id", unique = true, nullable = false)
    @Type(type = "uuid-char")
    var externalId: UUID?,

    @Column(name = "state_ref", nullable = false)
    @Convert(converter = StateRefToTextConverter::class)
    @ElementCollection
    var stateRef: List<StateRef>?


) : MappedSchema(AllowedToSeeStateMapping::class.java, 1, listOf(AllowedToSeeStateMapping::class.java)) {
    constructor() : this(null, null)
}

@Converter
class StateRefToTextConverter : AttributeConverter<StateRef, String> {
    val SEPERATOR: String = "|"

    override fun convertToDatabaseColumn(stateRef: StateRef?): String? = stateRef?.txhash.toString().plus(SEPERATOR).plus(stateRef?.index.toString())

    override fun convertToEntityAttribute(text: String?): StateRef? {
        val parts = text?.split(SEPERATOR)
        return parts?.let { StateRef(SecureHash.parse(it[0]), it[1].toInt()) }

    }
}