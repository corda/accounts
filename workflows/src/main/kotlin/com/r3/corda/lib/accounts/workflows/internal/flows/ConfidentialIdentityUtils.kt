package com.r3.corda.lib.accounts.workflows.internal.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.ourIdentity
import com.r3.corda.lib.ci.registerKeyToParty
import net.corda.core.CordaInternal
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.identity.AnonymousParty
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


@CordaInternal
fun createKeyForAccount(accountInfo: AccountInfo, serviceHub: ServiceHub) : AnonymousParty {
    val newKey = serviceHub.keyManagementService.freshKey(accountInfo.identifier.id)
    registerKeyToParty(newKey, serviceHub.ourIdentity, serviceHub)
    return AnonymousParty(newKey)
}