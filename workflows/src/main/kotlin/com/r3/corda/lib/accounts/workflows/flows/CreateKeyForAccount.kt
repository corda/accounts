package com.r3.corda.lib.accounts.workflows.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.ci.registerKeyToParty
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AnonymousParty
import java.security.PublicKey

/**
 * This flow should be used when you want to generate a new [PublicKey] for an account that owned by the node running
 * the flow.
 */
class CreateKeyForAccount(
        private val accountInfo: AccountInfo
) : FlowLogic<AnonymousParty>() {

    override fun call(): AnonymousParty {
        val newKey = serviceHub.keyManagementService.freshKey(accountInfo.identifier.id)
        registerKeyToParty(newKey, ourIdentity, serviceHub)
        return AnonymousParty(newKey)
    }
}