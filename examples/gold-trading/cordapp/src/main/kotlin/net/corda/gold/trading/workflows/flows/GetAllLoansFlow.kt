package net.corda.gold.trading.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.gold.trading.contracts.states.LoanBook
import java.util.*

@StartableByRPC
class GetAllLoansFlow(val accountIds: List<UUID>? = null) : FlowLogic<List<StateAndRef<LoanBook>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<LoanBook>> {
        return if (accountIds == null) {
            serviceHub.vaultService.queryBy(LoanBook::class.java).states
        } else {
            serviceHub.vaultService.queryBy<LoanBook>(QueryCriteria.VaultQueryCriteria(externalIds = accountIds)).states
        }
    }
}