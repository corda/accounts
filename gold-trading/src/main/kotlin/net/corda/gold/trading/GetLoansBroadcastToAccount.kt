package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

@StartableByRPC
@StartableByService
class GetLoansBroadcastToAccounts(val accounts: List<UUID>) : FlowLogic<List<StateAndRef<LoanBook>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<LoanBook>> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.broadcastedToAccountVaultQuery(
            accounts,
            QueryCriteria.VaultQueryCriteria(contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>
    }

}