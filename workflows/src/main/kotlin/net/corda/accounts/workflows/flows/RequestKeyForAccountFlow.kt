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
        val newKeyAndCert = if (accountInfo.host == ourIdentity) {
            serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false, accountInfo.id)
        } else {
            val session = initiateFlow(accountInfo.host)
            session.send(accountInfo.id)
            val accountSearchStatus = session.receive(AccountSearchStatus::class.java).unwrap { it }

            if (accountSearchStatus == AccountSearchStatus.NOT_FOUND) {
                throw IllegalStateException("Account Host: ${accountInfo.host} for ${accountInfo.id} (${accountInfo.name}) responded with a not found status - contact them for assistance")
            }
            val newKeyAndCert = session.receive<PartyAndCertificate>().unwrap { it }
            serviceHub.identityService.verifyAndRegisterIdentity(newKeyAndCert)
            serviceHub.withEntityManager {
                serviceHub.withEntityManager {
                    persist(PublicKeyHashToExternalId(accountInfo.id, newKeyAndCert.owningKey))
                }
            }
            newKeyAndCert
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