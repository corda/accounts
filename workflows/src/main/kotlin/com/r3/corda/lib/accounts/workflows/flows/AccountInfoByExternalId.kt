package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.util.*

/**
 * Returns the [AccountInfo]s for a specified external ID.
 *
 * @property externalId the account external ID to return the [AccountInfo] for
 */
@StartableByRPC
class AccountInfoByExternalId(private val externalId: String) : FlowLogic<List<StateAndRef<AccountInfo>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {
        return accountService.accountInfoByExternalId(externalId)
    }
}
