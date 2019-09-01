package com.r3.corda.lib.accounts.workflows

import com.r3.corda.lib.accounts.contracts.internal.schemas.PersistentAccountInfo
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountObservedQueryBy
import com.r3.corda.lib.accounts.workflows.internal.accountObservedTrackBy
import com.r3.corda.lib.accounts.workflows.internal.schemas.AllowedToSeeStateMapping
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.ci.registerKeyToParty
import net.corda.core.CordaInternal
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.node.services.vault.VaultSchemaV1
import java.util.*

/** Helper for obtaining a [KeyManagementBackedAccountService]. */
val FlowLogic<*>.accountService get() = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)

// TODO: Remove this and replace with a utility in a commons CorDapp.
val ServiceHub.ourIdentity get() = myInfo.legalIdentities.first()

// Query utilities.

/** Returns the base [AccountInfo] query criteria. */
val accountBaseCriteria = QueryCriteria.VaultQueryCriteria(
        contractStateTypes = setOf(AccountInfo::class.java),
        status = Vault.StateStatus.UNCONSUMED
)

/** To query [AccountInfo]s by host. */
fun accountHostCriteria(host: Party): QueryCriteria {
    return builder {
        val partySelector = PersistentAccountInfo::host.equal(host)
        QueryCriteria.VaultCustomQueryCriteria(partySelector)
    }
}

/** To query [AccountInfo]s by name. */
fun accountNameCriteria(name: String): QueryCriteria {
    return builder {
        val nameSelector = PersistentAccountInfo::name.equal(name)
        QueryCriteria.VaultCustomQueryCriteria(nameSelector)
    }
}

/** To query [AccountInfo]s by id. */
fun accountUUIDCriteria(id: UUID): QueryCriteria {
    return builder {
        val idSelector = PersistentAccountInfo::id.equal(id)
        QueryCriteria.VaultCustomQueryCriteria(idSelector)
    }
}

/** To query [ContractState]s by which account the participant keys are linked to. */
fun externalIdCriteria(accountIds: List<UUID>): QueryCriteria {
    // TODO: This requires a dependency on corda-node which should be removed.
    return builder {
        val externalIdSelector = VaultSchemaV1.StateToExternalId::externalId.`in`(accountIds)
        QueryCriteria.VaultCustomQueryCriteria(externalIdSelector)
    }
}

/** To query [ContractState]s by which an account has been allowed to see an an observer. */
fun allowedToSeeCriteria(accountIds: List<UUID>): QueryCriteria {
    return builder {
        val allowedToSeeSelector = AllowedToSeeStateMapping::externalId.`in`(accountIds)
        QueryCriteria.VaultCustomQueryCriteria(allowedToSeeSelector)
    }
}

/**
 * This only works on Corda 4 if both the "allow to see" table and the "external id to state table" contains rows. This
 * is a bug due to Hibernate using CROSS JOIN instead of LEFT JOIN, the result is that if either of the tables contains
 * no rows, then the resultant query returns no rows, when some should be returned. This will be fixed in Corda 5.
 *
 * The workaround, for now, is to perform queries for states observed by an account, separate to queries for states
 * owned by an account. Some temporary utilities have been provided to help you with this. See: [accountObservedQueryBy]
 * and [accountObservedTrackBy].
 *
 * TODO: Check status of CORDA-3038 (https://r3-cev.atlassian.net/browse/CORDA-3038).
 */
fun accountQueryCriteria(accountIds: List<UUID>): QueryCriteria {
    return allowedToSeeCriteria(accountIds).or(externalIdCriteria(accountIds))
}
