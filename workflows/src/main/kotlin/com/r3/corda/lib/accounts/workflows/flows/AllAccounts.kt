package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

/**
 * Returns all [AccountInfo]s known by this node.
 */
@StartableByRPC
class AllAccounts : FlowLogic<List<StateAndRef<AccountInfo>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> = accountService.allAccounts()
}