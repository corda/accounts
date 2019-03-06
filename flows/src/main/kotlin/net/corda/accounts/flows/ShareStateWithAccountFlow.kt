package net.corda.accounts.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.schemas.DirectStatePersistable
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareStateWithAccountFlow<T : ContractState>(val accountInfo: AccountInfo, val state: StateAndRef<T>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
        val session = initiateFlow(accountInfo.accountHost)
        subFlow(SendTransactionFlow(session, transaction!!))
        session.send(state.ref)
        session.send(accountInfo)
        val result = session.receive<ResultOfPermissioning>().unwrap { it }
        if (result == ResultOfPermissioning.FAIL) {
            throw FlowException("Counterparty failed to permission state")
        }

    }
}

@InitiatedBy(ShareStateWithAccountFlow::class)
class ReceiveStateForAccountFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
        val stateToPermission = otherSession.receive<StateRef>().unwrap { it }
        val accountInfo = otherSession.receive<AccountInfo>().unwrap { it }

        try {
            serviceHub.withEntityManager {
                val newEntry = AllowedToSeeStateMapping(null, accountInfo.accountId, PersistentStateRef(stateToPermission))
                persist(newEntry)
            }
            otherSession.send(ResultOfPermissioning.OK)
        } catch (e: Exception) {
            contextLogger().error("Permissioning error:", e)
            otherSession.send(ResultOfPermissioning.FAIL)
        }
    }
}

@CordaSerializable
enum class ResultOfPermissioning {
    OK, FAIL
}


@Entity
@Table(name = "account_to_state_refs", indexes = [Index(name = "external_id_idx", columnList = "external_id")])
data class AllowedToSeeStateMapping(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long?,

    @Column(name = "external_id", unique = false, nullable = false)
    @Type(type = "uuid-char")
    var externalId: UUID?,

    override var stateRef: PersistentStateRef?
) : DirectStatePersistable, MappedSchema(AllowedToSeeStateMapping::class.java, 1, listOf(AllowedToSeeStateMapping::class.java)) {
    constructor() : this(null, null, null)
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