package net.corda.gold.trading.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.services.queryBy
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria

@StartableByRPC
class GetAllLoansOwnedByAccountFlow(private val account: StateAndRef<AccountInfo>) : FlowLogic<List<StateAndRef<LoanBook>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<LoanBook>> {
        val criteria = QueryCriteria.VaultQueryCriteria(
                status = Vault.StateStatus.UNCONSUMED,
                contractStateTypes = setOf(LoanBook::class.java)
        )
        return serviceHub.vaultService.queryBy<LoanBook>(
                accountIds = listOf(account.state.data.id.id),
                criteria = criteria
        ).states
    }
}