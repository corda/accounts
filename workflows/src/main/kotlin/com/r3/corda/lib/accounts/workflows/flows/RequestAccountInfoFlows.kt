package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap
import java.util.*

/**
 * This flow can be used to check whether an account is hosted by a particular host. If it is then the host will share
 * the account info with the requester. This flow assumes that the requester already knows the account ID as perhaps
 * it was obtained through some other means.
 *
 * @property id id to request the [AccountInfo] for.
 * @property host session to request the [AccountInfo] from.
 */
class RequestAccountInfoFlow(val id: UUID, val host: FlowSession) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val hasAccount = host.sendAndReceive<Boolean>(id).unwrap { it }
        return if (hasAccount) subFlow(com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfoHandlerFlow(host)) else null
    }
}

/** Responder flow for [RequestAccountInfoFlow]. */
class RequestAccountInfoHandlerFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val response = otherSession.receive<UUID>().unwrap { accountService.accountInfo(it) }
        if (response == null) {
            otherSession.send(false)
        } else {
            subFlow(com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfoFlow(response, listOf(otherSession)))
        }
    }
}

// Initiating versions of the above flows.

/**
 * Shares an [AccountInfo] [StateAndRef] with the supplied [Party]s. The [AccountInfo] is always stored using
 * [StatesToRecord.ALL_VISIBLE].
 *
 * @property id id to request the [AccountInfo] for.
 * @property host [Party] to request the [AccountInfo] from.
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class RequestAccountInfo(val id: UUID, val host: Party) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val session = initiateFlow(host)
        return subFlow(RequestAccountInfoFlow(id, session))
    }
}

@InitiatedBy(RequestAccountInfo::class)
class RequestAccountInfoHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(RequestAccountInfoHandlerFlow(otherSession))
    }
}
