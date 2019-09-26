package com.r3.corda.lib.accounts.workflows.internal.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.CordaInternal
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
internal enum class AccountSearchStatus {
    FOUND,
    NOT_FOUND
}

@CordaInternal
fun ServiceHub.createKeyForAccount(accountInfo: AccountInfo) : AnonymousParty {
    val newKey = this.keyManagementService.freshKey(accountInfo.identifier.id)
    return AnonymousParty(newKey)
}