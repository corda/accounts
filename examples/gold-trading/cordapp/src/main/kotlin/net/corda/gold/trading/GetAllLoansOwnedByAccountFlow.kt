package net.corda.gold.trading

import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria

@StartableByRPC
class GetAllLoansOwnedByAccountFlow(private val account: StateAndRef<AccountInfo>) : FlowLogic<List<StateAndRef<LoanBook>>>() {


    override fun call(): List<StateAndRef<LoanBook>> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.ownedByAccountVaultQuery(
                account.state.data.accountId,
                QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(LoanBook::class.java))
        ) as List<StateAndRef<LoanBook>>

    }

}