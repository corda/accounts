package net.corda.accounts

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.OpaqueBytes

class OpenNewAccountFlow(val id: String) : FlowLogic<Account>() {

    @Suspendable
    override fun call(): Account {
        val ident = OpaqueBytes(id.toByteArray())
        val freshKey = serviceHub.keyManagementService.freshKey()
        return serviceHub.withEntityManager {
            val accountCreated = Account(ident, freshKey, serviceHub.myInfo.legalIdentities.first())
            persist(accountCreated)
            accountCreated
        }
    }

}