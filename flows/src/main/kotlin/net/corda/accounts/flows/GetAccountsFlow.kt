package net.corda.accounts.flows

import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class GetAccountsFlow(val oursOnly: Boolean) : FlowLogic<List<StateAndRef<AccountInfo>>>(){
    override fun call(): List<StateAndRef<AccountInfo>> {
        val cordaService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return if (oursOnly){
            cordaService.myAccounts()
        }else{
            cordaService.allAccounts()
        }
    }

}