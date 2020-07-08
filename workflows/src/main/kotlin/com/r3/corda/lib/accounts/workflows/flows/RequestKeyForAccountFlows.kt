package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.ProvideKeyFlow
import com.r3.corda.lib.ci.workflows.RequestKeyFlow
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

/**
 * This flow should be used when you want to generate a new [PublicKey] for an account that is not owned by the node running
 * the flow. [RequestKeyFlow] is called which which requests a new key-pair from the counter-party.
 *
 * @property accountInfo the account to request a new key for
 * @property hostSession the session for the node which hosts the supplied [AccountInfo]
 */
class RequestKeyForAccountFlow(
        private val accountInfo: AccountInfo,
        private val hostSession: FlowSession
) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        // Make sure we are contacting the correct host.
        require(hostSession.counterparty == accountInfo.host) {
            "The session counterparty must be the same as the account info host."
        }
        // The account is hosted on the initiating node. So we can generate a key and register it with the identity
        // service locally.
        return if (hostSession.counterparty == ourIdentity) {
            serviceHub.createKeyForAccount(accountInfo)
        } else {
            hostSession.send(accountInfo.identifier.id)
            val newKey = subFlow(RequestKeyFlow(hostSession, accountInfo.identifier.id)).owningKey
            // Store a local mapping of the account ID to the public key we've just received from the host.
            // This allows us to look up the account which the PublicKey is linked to in the future.
            // Note that this mapping of KEY -> PARTY persists even when an account moves to another node, the
            // assumption being that keys are not moved with the account. If keys DO move with accounts then
            // a new API must be added to REPLACE KEY -> PARTY mappings.
            //
            // The PublicKeyHashToAccountIdMapping table has a primary key constraint over PublicKey, therefore
            // a key can only ever be stored once. If you try to store a key twice, then an exception will be
            // thrown in respect of the primary key constraint violation.
            AnonymousParty(newKey)
        }
    }
}

/**
 * Responder flow for [RequestKeyForAccountFlow].
 */
class SendKeyForAccountFlow(private val otherSide: FlowSession) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        // No need to do anything if the initiating node is us. We can generate a key locally.
        if (otherSide.counterparty == ourIdentity) {
            throw FlowException("Should not call on your own")
        }
        val requestedAccountForKey = otherSide.receive(UUID::class.java).unwrap { it }
        val existingAccountInfo = accountService.accountInfo(requestedAccountForKey)
        if (existingAccountInfo == null) {
            throw FlowException("Account for $requestedAccountForKey not found")
        }
        return subFlow(ProvideKeyFlow(otherSide))
    }
}

// Initiating flows which can be started via RPC or a service. Calling these as a sub-flow from an existing flow will
// result in a new session being created.
@InitiatingFlow
@StartableByRPC
@StartableByService
class RequestKeyForAccount(private val accountInfo: AccountInfo) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        val hostSession = initiateFlow(accountInfo.host)
        return subFlow(RequestKeyForAccountFlow(accountInfo, hostSession))
    }
}

@InitiatedBy(RequestKeyForAccount::class)
class SendKeyForAccount(private val otherSide: FlowSession) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call() = subFlow(SendKeyForAccountFlow(otherSide))
}