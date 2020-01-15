package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.AccountSearchStatus
import net.corda.core.flows.*
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

/**
 * This flow can be used to share a [PublicKey]-[AccountInfo] mapping with another host.
 * Just like the use of [RequestKeyForAccountFlow] handle such mappings from remote accounts,
 * this flow allows the remote host to handle the mapping for the provided key.
 *
 * @property accountKey the [PublicKey] to share.
 * @property hostSession session to share the [accountKey] with.
 */
class ShareAccountKeyFlow(private val accountKey: PublicKey,
                          private val hostSession: FlowSession)
    : FlowLogic<Unit>()
{
    @Suspendable
    override fun call() {
        if (serviceHub.myInfo.legalIdentities.contains(hostSession.counterparty)) {
            return
        }

        val accountInfo = accountService.accountInfo(accountKey)?.state?.data
                ?: throw FlowException("Cannot find any account for the given key on this node.")

        val accountSearchStatus = hostSession.sendAndReceive<AccountSearchStatus>(accountInfo.identifier.id).unwrap { it }
        if (accountSearchStatus == AccountSearchStatus.NOT_FOUND) {
            // In order to bind the provided key to an account, the latter must be known by the receiver host.
            accountService.shareAccountInfoWithParty(accountInfo.identifier.id, hostSession.counterparty)
        }

        hostSession.send(accountKey)
    }
}

/**
 * Responder flow for [ShareAccountKeyFlow].
 */
class ShareAccountKeyHandlerFlow(private val providerSession: FlowSession)
    : FlowLogic<Unit>()
{
    @Suspendable
    override fun call() {
        if (serviceHub.myInfo.legalIdentities.contains(providerSession.counterparty)) {
            return
        }

        val accountUUID = providerSession.receive<UUID>().unwrap { it }

        val existingAccountInfo = accountService.accountInfo(accountUUID)
        if (existingAccountInfo == null) {
            providerSession.send(AccountSearchStatus.NOT_FOUND)
        } else {
            providerSession.send(AccountSearchStatus.FOUND)
        }

        val accountKey = providerSession.receive<PublicKey>().unwrap { it }

        serviceHub.identityService.registerKey(accountKey, providerSession.counterparty, accountUUID)
    }
}