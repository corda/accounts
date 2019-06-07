package net.corda.accounts.workflows

import net.corda.accounts.contracts.schemas.PersistentAccountInfo
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import java.util.*

val FlowLogic<*>.accountService get() = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)

val ServiceHub.ourIdentity get() = myInfo.legalIdentities.first()

// Query utilities.

/** Returns the base account info query criteria. */
val accountBaseCriteria = QueryCriteria.VaultQueryCriteria(
        contractStateTypes = setOf(AccountInfo::class.java),
        status = Vault.StateStatus.UNCONSUMED
)

fun accountHostCriteria(host: Party): QueryCriteria {
    return builder {
        val partySelector = PersistentAccountInfo::host.equal(host)
        QueryCriteria.VaultCustomQueryCriteria(partySelector)
    }
}

fun accountNameCriteria(name: String): QueryCriteria {
    return builder {
        val nameSelector = PersistentAccountInfo::name.equal(name)
        QueryCriteria.VaultCustomQueryCriteria(nameSelector)
    }
}

fun accountUUIDCriteria(id: UUID): QueryCriteria {
    return builder {
        val idSelector = PersistentAccountInfo::id.equal(id)
        QueryCriteria.VaultCustomQueryCriteria(idSelector)
    }
}