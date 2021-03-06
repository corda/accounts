package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

/**
 * Returns all the [AccountInfo]s for the specified host [Party].
 *
 * @property host the host [Party] to return accounts for.
 */
@StartableByRPC
class AccountsForHost(private val host: Party) : FlowLogic<List<StateAndRef<AccountInfo>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> = accountService.accountsForHost(host)
}