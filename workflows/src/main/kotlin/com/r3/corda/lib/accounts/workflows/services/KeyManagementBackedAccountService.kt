package com.r3.corda.lib.accounts.workflows.services

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.*
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareStateWithAccount
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture

@CordaService
class KeyManagementBackedAccountService(val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {

    companion object {
        val logger = contextLogger()
    }

    override fun accountsForHost(host: Party): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(accountHostCriteria(host))).states
    }

    override fun ourAccounts(): List<StateAndRef<AccountInfo>> {
        return accountsForHost(services.ourIdentity)
    }

    override fun allAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria).states
    }

    override fun accountInfo(id: UUID): StateAndRef<AccountInfo>? {
        val uuidCriteria = accountUUIDCriteria(id)
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(uuidCriteria)).states.singleOrNull()
    }

    override fun accountInfo(name: String): List<StateAndRef<AccountInfo>> {
        val nameCriteria = accountNameCriteria(name)
        val results = services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(nameCriteria)).states
        return when (results.size) {
            0 -> emptyList()
            1 -> listOf(results.single())
            else -> {
                logger.warn("WARNING: Querying for account by name returned more than one account, this is likely " +
                        "because another node shared an account with this node that has the same name as an " +
                        "account already created on this node. Filtering the results by host will allow you to access" +
                        "the AccountInfo you need.")
                results
            }
        }
    }

    override fun accountInfoByExternalId(externalId: String): List<StateAndRef<AccountInfo>> {
        val externalIdCriteria = accountExternalIdCriteria(externalId)
        val results = services.vaultService.queryBy<AccountInfo>(
            accountBaseCriteria.and(externalIdCriteria)).states
        return when (results.size) {
            0 -> emptyList()
            1 -> listOf(results.single())
            else -> {
                logger.warn("WARNING: Querying for account by externalId returned more than one account, this is likely " +
                        "because another node shared an account with this node that has the same name as an " +
                        "account already created on this node. Filtering the results by host will allow you to access" +
                        "the AccountInfo you need.")
                results
            }
        }
    }

    @Suspendable
    override fun createAccount(name: String): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(CreateAccount(name))
    }

    @Suspendable
    override fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: Party): CordaFuture<Unit> {
        return flowAwareStartFlow(ShareStateAndSyncAccounts(state, party))
    }

    override fun accountKeys(id: UUID): List<PublicKey> {
        return services.identityService.publicKeysForExternalId(id).toList()
    }

    override fun accountIdForKey(owningKey: PublicKey): UUID? {
        return services.identityService.externalIdForPublicKey(owningKey)
    }

    override fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>? {
        return accountIdForKey(owningKey)?.let { accountInfo(it) }
    }

    @Suspendable
    override fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareAccountInfo(foundAccount, listOf(party)))
        } else {
            CompletableFuture<Unit>().also {
                it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node"))
            }.asCordaFuture()
        }
    }

    @Suspendable
    override fun <T : ContractState> shareStateWithAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareStateWithAccount(accountInfo = foundAccount.state.data, state = state))
        } else {
            CompletableFuture<Unit>().also {
                it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node"))
            }.asCordaFuture()
        }
    }

    @Suspendable
    private inline fun <reified T : Any> flowAwareStartFlow(flowLogic: FlowLogic<T>): CordaFuture<T> {
        val currentFlow = FlowLogic.currentTopLevel
        return if (currentFlow != null) {
            val result = currentFlow.subFlow(flowLogic)
            doneFuture(result)
        } else {
            this.services.startFlow(flowLogic).returnValue
        }
    }
}