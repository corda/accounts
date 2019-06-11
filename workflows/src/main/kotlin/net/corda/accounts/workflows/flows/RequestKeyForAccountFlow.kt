package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.accountService
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.node.services.keys.PublicKeyHashToExternalId
import java.util.*

@InitiatingFlow
@StartableByService
@StartableByRPC
class RequestKeyForAccountFlow(private val accountInfo: AccountInfo) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        // If the host is the node running this flow then generate a new CI locally and return it. Otherwise call out
        // to the remote host and ask THEM to generate a new CI and send it back. We cannot use the existing CI flows
        // here because they don't allow us to supply an external ID when the new CI is created.
        // TODO: Replace use of the old CI API With the new API.
        val newKeyAndCert = if (accountInfo.host == ourIdentity) {
            serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false, accountInfo.id)
        } else {
            val session = initiateFlow(accountInfo.host)
            val accountSearchStatus = session.sendAndReceive<AccountSearchStatus>(accountInfo.id).unwrap { it }
            when (accountSearchStatus) {
                AccountSearchStatus.NOT_FOUND -> throw IllegalStateException("Account Host: ${accountInfo.host} for " +
                        "${accountInfo.id} (${accountInfo.name}) responded with a not found status - contact them " +
                        "for assistance")
                AccountSearchStatus.FOUND -> {
                    val newKeyAndCert = session.receive<PartyAndCertificate>().unwrap { it }
                    serviceHub.identityService.verifyAndRegisterIdentity(newKeyAndCert)
                    // TODO: Should we store keys created on other nodes in this table?
                    // I think the initial assumption was that this table was for locally created keys only. If a node
                    // operator runs a report over this table, how do they distinguish between their accounts/keys and
                    // accounts/keys created on another node?
                    serviceHub.withEntityManager {
                        persist(PublicKeyHashToExternalId(accountInfo.id, newKeyAndCert.owningKey))
                    }
                    newKeyAndCert
                }
            }
        }
        return AnonymousParty(newKeyAndCert.owningKey)
    }
}


@InitiatedBy(RequestKeyForAccountFlow::class)
class SendKeyForAccountFlow(val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val requestedAccountForKey = otherSide.receive(UUID::class.java).unwrap { it }
        val existingAccountInfo = accountService.accountInfo(requestedAccountForKey)
        if (existingAccountInfo == null) {
            otherSide.send(AccountSearchStatus.NOT_FOUND)
        } else {
            otherSide.send(AccountSearchStatus.FOUND)
            val freshKeyAndCert = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false, requestedAccountForKey)
            otherSide.send(freshKeyAndCert)
        }
    }

}

@CordaSerializable
enum class AccountSearchStatus {
    FOUND, NOT_FOUND
}

@StartableByService
@StartableByRPC
class SetAccountKeyPolicyFlow(val accountId: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        TODO("not available in V4 Corda") //To change body of created functions use File | Settings | File Templates.
    }

}

@StartableByService
@StartableByRPC
class GetAccountKeyPolicyFlow(val accountId: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        TODO("not available in V4 Corda") //To change body of created functions use File | Settings | File Templates.
    }
}

enum class AccountKeyPolicy {
    REUSE, FRESH
}