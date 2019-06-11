package net.corda.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.accountService
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.unwrap
import net.corda.node.services.keys.PublicKeyHashToExternalId
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

@CordaSerializable
internal enum class AccountSearchStatus {
    FOUND,
    NOT_FOUND
}

@CordaSerializable
internal data class CertificateOwnershipAssertion(val name: CordaX500Name, val owningKey: PublicKey)

@CordaSerializable
internal data class IdentityWithSignature(val identity: PartyAndCertificate, val signature: DigitalSignature)

internal fun buildDataToSign(identity: PartyAndCertificate): ByteArray {
    return CertificateOwnershipAssertion(identity.name, identity.owningKey).serialize().bytes
}

internal fun validateAndRegisterIdentity(
        serviceHub: ServiceHub,
        otherSide: Party,
        theirAnonymousIdentity: PartyAndCertificate,
        signature: DigitalSignature
): PartyAndCertificate {
    if (theirAnonymousIdentity.name != otherSide.name) {
        throw Exception("Certificate subject must match counterparty's well known identity.")
    }
    try {
        theirAnonymousIdentity.owningKey.verify(buildDataToSign(theirAnonymousIdentity), signature)
    } catch (ex: SignatureException) {
        throw Exception("Signature does not match the expected identity ownership assertion.", ex)
    }
    // Validate then store their identity so that we can prove the key in the transaction is owned by the counterparty.
    serviceHub.identityService.verifyAndRegisterIdentity(theirAnonymousIdentity)
    return theirAnonymousIdentity
}

class RequestKeyForAccountFlow(
        private val accountInfo: AccountInfo,
        private val hostSession: FlowSession
) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        // TODO: Replace use of the old CI API With the new API.
        // If the host is the node running this flow then generate a new CI locally and return it. Otherwise call out
        // to the remote host and ask THEM to generate a new CI and send it back. We cannot use the existing CI flows
        // here because they don't allow us to supply an external ID when the new CI is created.
        val newKeyAndCert = if (accountInfo.host == ourIdentity) {
            serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false, accountInfo.id)
        } else {
            val accountSearchStatus = hostSession.sendAndReceive<AccountSearchStatus>(accountInfo.id).unwrap { it }
            when (accountSearchStatus) {
                AccountSearchStatus.NOT_FOUND -> {
                    throw IllegalStateException("Account Host: ${accountInfo.host} for ${accountInfo.id} " +
                            "(${accountInfo.name}) responded with a not found status - contact them for assistance")
                }
                AccountSearchStatus.FOUND -> {
                    val newKeyAndCert = hostSession.receive<IdentityWithSignature>().unwrap { it }
                    validateAndRegisterIdentity(
                            serviceHub = serviceHub,
                            otherSide = accountInfo.host,
                            theirAnonymousIdentity = newKeyAndCert.identity,
                            signature = newKeyAndCert.signature
                    )
                    // Store a local mapping of the account ID to the public key we've just received from the host.
                    // This allows us to look up the account which the PublicKey is linked to in the future.
                    serviceHub.withEntityManager {
                        persist(PublicKeyHashToExternalId(accountInfo.id, newKeyAndCert.identity.owningKey))
                    }
                    newKeyAndCert.identity
                }
            }
        }
        return AnonymousParty(newKeyAndCert.owningKey)
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
            val freshKeyAndCert = serviceHub.keyManagementService.freshKeyAndCert(
                    identity = ourIdentityAndCert,
                    revocationEnabled = false,
                    externalId = requestedAccountForKey
            )
            val data: ByteArray = buildDataToSign(freshKeyAndCert)
            val signature = serviceHub.keyManagementService.sign(data, freshKeyAndCert.owningKey).withoutKey()
            val keyWithSignature = IdentityWithSignature(freshKeyAndCert, signature)
            otherSide.send(keyWithSignature)
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