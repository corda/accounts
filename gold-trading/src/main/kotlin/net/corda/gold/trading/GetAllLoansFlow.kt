package net.corda.gold.trading

import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class GetAllLoansFlow : FlowLogic<List<StateAndRef<LoanBook>>>() {

    override fun call(): List<StateAndRef<LoanBook>> {
        return serviceHub.vaultService.queryBy(LoanBook::class.java).states
    }

}