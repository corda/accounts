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

/**
 * This flow shares a [StateAndRef] with an account as opposed to a full node. The difference is subtle and worth
 * explaining here. When an account receives a [StateAndRef], it is stored in the vault of the host node using
 * [StatesToRecord.ALL_VISIBLE], this means that the node administrator can see all [StateAndRef]s which have been sent
 * to accounts which they host. As well as storing the state in the vault, the host node also permissions the
 * [StateAndRef] such that only the owner of the specified account can see it. Any other accounts on the same node
 * will not be able to see the state. This permissioning is done at the application level through vault queries, see
 * [accountQueryCriteria] for further information. This does mean that if account holders have access to the node via
 * RPC directly, then they can "override" the account level query and potentially see states permissioned to other
 * accounts. The remedy for this is to ensure that account holders only access their accounts via an application which
 * correctly handles state level permissioning.
 *
 * Permissioning for "observed" states is required, where as for "held" states it is not because when an account holder
 * holds a state, the public key used to hold that state links back to the account id - the node hold holds a mapping of
 * account IDs to public keys which can be used when querying the vault by account ID. However, in the case of "observed"
 * states which are not held by an account but can be seen by an account, there is no participant public key inside the
 * state attributable to the account holder, therefore we must store a separate table of information pertaining to the
 * set of states which can account is allowed to see. See the [AllowedToSeeStateMapping] for further details.
 *
 * @property accountInfo the account to share the [StateAndRef] with
 * @property state the [StateAndRef] to share
 * @property hostSession an existing [FlowSession] with the host node
 */
class ShareStateWithAccountFlow<T : ContractState>(
        val accountInfo: AccountInfo,
        val state: StateAndRef<T>,
        val hostSession: FlowSession
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transaction = serviceHub.validatedTransactions.getTransaction(state.ref.txhash)
                ?: throw FlowException("Can't find transaction with hash ${state.ref.txhash}")
        subFlow(SendTransactionFlow(hostSession, transaction))
        hostSession.send(state.ref)
        hostSession.send(accountInfo)
        val result = hostSession.receive<ResultOfPermissioning>().unwrap { it }
        if (result == ResultOfPermissioning.FAIL) {
            throw FlowException("Counterparty failed to permission state.")
        }
    }
}

/**
 * Responder flow for [ShareStateWithAccountFlow].
 */
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

/**
 * A version of [ShareStateWithAccountFlow] which is initiating and startable via services and RPC.
 */
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

/** Responder flow for [ShareStateWithAccount]. */
@InitiatedBy(ShareStateWithAccount::class)
class ReceiveStateForAccount(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ReceiveStateForAccountFlow(otherSession))
}