package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap

/**
 * Alternative to [RequestAccountInfoFlow] that matches a hosted Account based on the `linearId.externalId` value.
 * Maintaining unique `externalId` values per host node is an application-level concern. An error will be thrown
 * if multiple matches are found.
 *
 * @property `externalId` the `linearId.externalId` value to request [AccountInfo]s for.
 * @property session session to request the [AccountInfo] from.
 */
class RequestHostedAccountInfoByExternalIdFlow(val externalId: String, val session: FlowSession) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val hasAccount = session.sendAndReceive<Boolean>(externalId).unwrap { it }
        return if (hasAccount) subFlow(ShareAccountInfoHandlerFlow(session)) else null
    }
}

/**
 * Responder flow for [RequestHostedAccountInfoByExternalIdFlow].
 */
class RequestHostedAccountInfoByExternalIdHandlerFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val requestedAccount = otherSession.receive<String>().unwrap {
            accountService.accountInfoByExternalId(it).singleOrNull { it.state.data.host == ourIdentity }
        }
        if (requestedAccount == null) {
            otherSession.send(false)
        } else {
            otherSession.send(true)
            subFlow(ShareAccountInfoFlow(requestedAccount, listOf(otherSession)))
        }
    }
}

// Initiating versions of the above flows.

/**
 * Shares an [AccountInfo] [StateAndRef] with the supplied [Party]s. The [AccountInfo] is always stored using
 * [StatesToRecord.ALL_VISIBLE].
 *
 * @property externalId identifier to request the [AccountInfo] for.
 * @property host [Party] to request the [AccountInfo] from.
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class RequestHostedAccountInfoByExternalId(val externalId: String, val host: Party) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val session = initiateFlow(host)
        return subFlow(RequestHostedAccountInfoByExternalIdFlow(externalId, session))
    }
}

/**
 * Responder flow for [RequestHostedAccountInfoByExternalId].
 */
@InitiatedBy(RequestHostedAccountInfoByExternalId::class)
class RequestHostedAccountInfoByExternalIdHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(RequestHostedAccountInfoByExternalIdHandlerFlow(otherSession))
    }
}