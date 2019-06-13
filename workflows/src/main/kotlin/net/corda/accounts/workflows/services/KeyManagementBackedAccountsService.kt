package net.corda.accounts.workflows.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.*
import net.corda.accounts.workflows.flows.CreateAccount
import net.corda.accounts.workflows.flows.ShareAccountInfo
import net.corda.accounts.workflows.flows.ShareStateAndSyncAccounts
import net.corda.accounts.workflows.flows.ShareStateWithAccount
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture

@CordaService
class KeyManagementBackedAccountService(val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {

    @Suspendable
    override fun accountsForHost(host: Party): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(accountHostCriteria(host))).states
    }

    @Suspendable
    override fun ourAccounts(): List<StateAndRef<AccountInfo>> {
        return accountsForHost(services.ourIdentity)
    }

    @Suspendable
    override fun allAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria).states
    }

    @Suspendable
    override fun accountInfo(id: UUID): StateAndRef<AccountInfo>? {
        val uuidCriteria = accountUUIDCriteria(id)
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(uuidCriteria)).states.singleOrNull()
    }

    @Suspendable
    override fun accountInfo(name: String): StateAndRef<AccountInfo>? {
        val nameCriteria = accountNameCriteria(name)
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(nameCriteria)).states.singleOrNull()
    }

    @Suspendable
    override fun createAccount(name: String): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(CreateAccount(name))
    }

    @Suspendable
    override fun createAccount(name: String, id: UUID): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(CreateAccount(name, id))
    }

    override fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: Party): CordaFuture<Unit> {
        return flowAwareStartFlow(ShareStateAndSyncAccounts(state, party))
    }

    @Suspendable
    override fun accountKeys(id: UUID): List<PublicKey> {
        return services.withEntityManager {
            val query = createQuery(
                    """
                        select a.$persistentKey_publicKey
                        from $persistentKey a, $publicKeyHashToExternalId b
                        where b.$publicKeyHashToExternalId_externalId = :uuid
                            and b.$publicKeyHashToExternalId_publicKeyHash = a.$persistentKey_publicKeyHash
                    """,
                    ByteArray::class.java
            )
            query.setParameter("uuid", id)
            query.resultList.map { Crypto.decodePublicKey(it) }
        }
    }

    @Suspendable
    override fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>? {
        val uuid = services.withEntityManager {
            val query = createQuery(
                    """
                        select $publicKeyHashToExternalId_externalId
                        from $publicKeyHashToExternalId
                        where $publicKeyHashToExternalId_publicKeyHash = :hash
                    """,
                    UUID::class.java
            )
            query.setParameter("hash", owningKey.toStringShort())
            query.resultList
        }
        return uuid.singleOrNull()?.let { accountInfo(it) }
    }

    @Suspendable
    override fun broadcastedToAccountVaultQuery(
            accountIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>> {
        return services.vaultService.queryBy<ContractState>(allowedToSeeCriteria(accountIds)).states
    }

    @Suspendable
    override fun broadcastedToAccountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        return broadcastedToAccountVaultQuery(listOf(accountId), queryCriteria)
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