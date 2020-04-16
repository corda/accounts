package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap

/**
 * This flow can be used to check whether an account is hosted by a particular host by passing account name. If it is then the host will share
 * the account info with the requester. This flow should be used in a situation where UUID is difficult to obtain for a given
 * account. For e.g: a node can create accounts for its employees using SSN/NIN or employeeId
 *
 * @property accountName account name to request the [AccountInfo] for hosted at [host] node.
 * @property host session to request the [AccountInfo] from.
 */
class RequestAccountInfoByNameFlow(private val accountName: String, val host: FlowSession) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val hasAccount = host.sendAndReceive<Boolean>(accountName).unwrap { it }
        return if (hasAccount) subFlow(ShareAccountInfoHandlerFlow(host)) else null
    }
}

/**
 * Responder flow for [RequestAccountInfoByNameFlow].
 */
class RequestAccountInfoByNameHandlerFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val accountName = otherSession.receive(String::class.java).unwrap {it }
        val response = serviceHub.accountService.accountInfo(accountName).find { it.state.data.host == ourIdentity }
        if (response == null) {
            otherSession.send(false)
        } else {
            otherSession.send(true)
            subFlow(ShareAccountInfoFlow(response, listOf(otherSession)))
        }
    }
}

// Initiating versions of the above flows.

/**
 * Shares an [AccountInfo] [StateAndRef] with the supplied [Party]s. The [AccountInfo] is always stored using
 * [StatesToRecord.ALL_VISIBLE].
 *
 * @property accountName account name to request the [AccountInfo] for.
 * @property host [Party] to request the [AccountInfo] from.
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class RequestAccountInfoByName(private val accountName: String, val host: Party) : FlowLogic<AccountInfo?>() {
    @Suspendable
    override fun call(): AccountInfo? {
        val session = initiateFlow(host)
        return subFlow(RequestAccountInfoByNameFlow(accountName, session))
    }
}

/**
 * Responder flow for [RequestAccountInfoByName].
 */
@InitiatedBy(RequestAccountInfoByName::class)
class RequestAccountInfoByNameHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(RequestAccountInfoByNameHandlerFlow(otherSession))
    }
}
