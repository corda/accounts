package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.schemas.AllowedToSeeStateMapping
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap

@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareStateWithAccountFlow<T : ContractState>(val accountInfo: AccountInfo, val state: StateAndRef<T>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
        val session = initiateFlow(accountInfo.host)
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
                val newEntry = AllowedToSeeStateMapping(null, accountInfo.id, PersistentStateRef(stateToPermission))
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


