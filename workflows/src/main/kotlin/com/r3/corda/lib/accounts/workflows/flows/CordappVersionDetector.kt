package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.util.*

/**
 * Returns the [AccountInfo]s for a specified account ID.
 *
 * @property uuid the account id to return the [AccountInfo] for
 */
@StartableByRPC
class CordappVersionDetector : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        return serviceHub.cordappProvider.getAppContext().cordapp.info.version
    }
}
