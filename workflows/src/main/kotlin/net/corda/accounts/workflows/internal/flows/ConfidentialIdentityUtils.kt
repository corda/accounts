package net.corda.accounts.workflows.internal.flows

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import java.security.PublicKey
import java.security.SignatureException

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