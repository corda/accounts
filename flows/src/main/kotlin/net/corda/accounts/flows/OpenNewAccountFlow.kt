package net.corda.accounts.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.model.Account
import net.corda.core.flows.FlowLogic

class OpenNewAccountFlow(val id: String) : FlowLogic<Account>() {

    @Suspendable
    override fun call(): Account {
        val freshKey = serviceHub.keyManagementService.freshKey()
        return serviceHub.withEntityManager {
            val accountCreated = Account(id.toByteArray(), freshKey, serviceHub.myInfo.legalIdentities.first())
            persist(accountCreated)
            accountCreated
        }
    }

}