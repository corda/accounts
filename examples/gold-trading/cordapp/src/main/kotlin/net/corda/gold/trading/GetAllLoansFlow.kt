package net.corda.gold.trading

import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

@StartableByRPC
class GetAllLoansFlow(val accountIds: List<UUID>? = null) : FlowLogic<List<StateAndRef<LoanBook>>>() {

    override fun call(): List<StateAndRef<LoanBook>> {
        return if (accountIds == null) {
            serviceHub.vaultService.queryBy(LoanBook::class.java).states
        } else {
            val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
            accountService.ownedByAccountVaultQuery(accountIds, QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(LoanBook::class.java))) as List<StateAndRef<LoanBook>>
        }
    }

}