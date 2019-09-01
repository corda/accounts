package com.r3.corda.lib.accounts.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

/**
 * Returns the [AccountInfo]s for a specified account name. Note that this may return more than one [AccountInfo] if
 * the node stores two accounts of the same name but with different hosts. E.g. "Roger@NodeA" and "Roger@NodeB", this is
 * possible because accounts names are not unique at the network level but the tuple of account name and host are
 * unique at the network level.
 *
 * @property name the account name to return the [AccountInfo] for
 */
@StartableByRPC
class AccountInfoByName(private val name: String) : FlowLogic<List<StateAndRef<AccountInfo>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {
        return accountService.accountInfo(name)
    }
}
