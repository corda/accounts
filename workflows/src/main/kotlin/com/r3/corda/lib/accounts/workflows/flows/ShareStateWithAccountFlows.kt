package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.schemas.AllowedToSeeStateMapping
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap

@CordaSerializable
internal enum class ResultOfPermissioning {
    OK, FAIL
}

class ShareStateWithAccountFlow<T : ContractState>(
        val accountInfo: AccountInfo,
        val state: StateAndRef<T>,
        val hostSession: FlowSession
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
        subFlow(SendTransactionFlow(hostSession, transaction!!))
        hostSession.send(state.ref)
        hostSession.send(accountInfo)
        val result = hostSession.receive<ResultOfPermissioning>().unwrap { it }
        if (result == ResultOfPermissioning.FAIL) {
            throw FlowException("Counterparty failed to permission state.")
        }
    }
}

class ReceiveStateForAccountFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
        val stateToPermission = otherSession.receive<StateRef>().unwrap { it }
        val accountInfo = otherSession.receive<AccountInfo>().unwrap { it }
        try {
            serviceHub.withEntityManager {
                val newEntry = AllowedToSeeStateMapping(
                        id = null,
                        externalId = accountInfo.linearId.id,
                        stateRef = PersistentStateRef(stateToPermission)
                )
                persist(newEntry)
            }
            otherSession.send(ResultOfPermissioning.OK)
        } catch (e: Exception) {
            contextLogger().error("Permissioning error:", e)
            otherSession.send(ResultOfPermissioning.FAIL)
        }
    }
}

// Initiating versions of the above flows.

@StartableByRPC
@StartableByService
@InitiatingFlow
class ShareStateWithAccount<T : ContractState>(
        val accountInfo: AccountInfo,
        val state: StateAndRef<T>
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val hostSession = initiateFlow(accountInfo.host)
        subFlow(ShareStateWithAccountFlow(accountInfo, state, hostSession))
    }
}

@InitiatedBy(ShareStateWithAccount::class)
class ReceiveStateForAccount(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ReceiveStateForAccountFlow(otherSession))
}