package net.corda.gold.trading.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.gold.trading.contracts.states.LoanBook

@StartableByRPC
class GetAllLoansOwnedByAccountFlow(private val account: StateAndRef<AccountInfo>) : FlowLogic<List<StateAndRef<LoanBook>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<LoanBook>> {
        val criteria = QueryCriteria.VaultQueryCriteria(
                status = Vault.StateStatus.UNCONSUMED,
                contractStateTypes = setOf(LoanBook::class.java),
                externalIds = listOf(account.state.data.identifier.id)
        )
        return serviceHub.vaultService.queryBy<LoanBook>(criteria = criteria).states
    }
}