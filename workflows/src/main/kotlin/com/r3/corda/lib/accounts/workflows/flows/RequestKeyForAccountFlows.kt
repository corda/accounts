package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.AccountSearchStatus
import com.r3.corda.lib.ci.RequestKeyInitiator
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.utilities.unwrap
import java.util.*

class RequestKeyForAccountFlow(
        private val accountInfo: AccountInfo,
        private val hostSession: FlowSession
) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        // If the account host is the node running this flow then generate a new CI locally and return it. Otherwise call out
        // to the remote host and ask THEM to generate a new CI and send it back.
        val newKey = if (accountInfo.host == ourIdentity) {
            subFlow(RequestKeyInitiator(ourIdentity, serviceHub.keyManagementService.freshKey(accountInfo.identifier.id))).publicKey
        } else {
            val accountSearchStatus = hostSession.sendAndReceive<AccountSearchStatus>(accountInfo.identifier.id).unwrap { it }
            when (accountSearchStatus) {
                AccountSearchStatus.NOT_FOUND -> {
                    throw IllegalStateException("Account Host: ${accountInfo.host} for ${accountInfo.identifier} " +
                            "(${accountInfo.name}) responded with a not found status - contact them for assistance")
                }
                AccountSearchStatus.FOUND -> {
                    val keyFromRemoteHost = subFlow(RequestKeyInitiator(hostSession.counterparty, accountInfo.identifier.id)).publicKey
                    keyFromRemoteHost
                }
            }
        }
        return AnonymousParty(newKey)
    }
}

class SendKeyForAccountFlow(val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val requestedAccountForKey = otherSide.receive(UUID::class.java).unwrap { it }
        val existingAccountInfo = accountService.accountInfo(requestedAccountForKey)
        if (existingAccountInfo == null) {
            otherSide.send(AccountSearchStatus.NOT_FOUND)
        } else {
            otherSide.send(AccountSearchStatus.FOUND)
        }
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
class SendKeyForAccount(val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(SendKeyForAccountFlow(otherSide))
}