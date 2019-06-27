package net.corda.gold.trading.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.gold.trading.workflows.schemas.WebAccountPermissioning
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@StartableByRPC
class GetAllWebUsersFlow : FlowLogic<List<String>>() {
    @Suspendable
    override fun call(): List<String> {
        return serviceHub.withEntityManager {
            val query = criteriaBuilder.createQuery(WebAccountPermissioning::class.java)
            val queryRoot = query.from(WebAccountPermissioning::class.java)
            query.select(queryRoot)
            createQuery(query).resultList.mapNotNull { it.webAccount }
        }
    }
}

@StartableByRPC
class GetWebUserFlow(val userToFind: String) : FlowLogic<WebAccountPermissioning?>() {
    @Suspendable
    override fun call(): WebAccountPermissioning? {
        val refHolder = AtomicReference<WebAccountPermissioning?>()
        serviceHub.withEntityManager(Consumer { em ->
            val foundAccount = em.find(WebAccountPermissioning::class.java, userToFind)
            val loadedAccount = foundAccount?.copy(permissionedAccounts = foundAccount.permissionedAccounts?.map { it })
            refHolder.set(loadedAccount)
        })
        return refHolder.get()
    }
}

@StartableByRPC
@StartableByService
class NewWebAccountFlow(val webAccount: String) : FlowLogic<WebAccountPermissioning>() {
    @Suspendable
    override fun call(): WebAccountPermissioning {
        return serviceHub.withEntityManager {
            val existingEntry = find(WebAccountPermissioning::class.java, webAccount);
            if (existingEntry != null) {
                throw IllegalStateException("Account: $webAccount already exists ")
            }

            val newEntry = WebAccountPermissioning(webAccount, listOf())
            persist(newEntry)
            newEntry
        }
    }
}


@StartableByRPC
@StartableByService
class PermissionWebLoginToAccountFlow(val webAccount: String, val accountToPermission: UUID, val add: Boolean = true) : FlowLogic<WebAccountPermissioning>() {
    @Suspendable
    override fun call(): WebAccountPermissioning {
        return serviceHub.withEntityManager {
            val existingEntry = find(WebAccountPermissioning::class.java, webAccount)
                    ?: WebAccountPermissioning(webAccount = webAccount, permissionedAccounts = listOf())
            val modifiedEntry = if (add) {
                existingEntry.permissionedAccounts = existingEntry.permissionedAccounts!! + listOf(accountToPermission)
                existingEntry
            } else {
                existingEntry.permissionedAccounts = existingEntry.permissionedAccounts!! - listOf(accountToPermission)
                existingEntry
            }
            return@withEntityManager modifiedEntry.also { persist(it) }
        }
    }
}

