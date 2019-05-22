package net.corda.accounts.service

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.flows.AllowedToSeeStateMapping
import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ShareAccountInfoWithNodes
import net.corda.accounts.flows.ShareStateWithAccountFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.hash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.keys.PublicKeyHashToExternalId
import net.corda.node.services.vault.VaultSchemaV1
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture

@CordaService
interface AccountService : SerializeAsToken {

    // Accounts which the calling node hosts.
    fun myAccounts(): List<StateAndRef<AccountInfo>>

    // Returns all accounts, including those hosted by other nodes.
    fun allAccounts(): List<StateAndRef<AccountInfo>>

    // Creates a new account and returns the AccountInfo StateAndRef.
    fun createAccount(accountName: String): CordaFuture<StateAndRef<AccountInfo>>

    // Overload for creating an account with a specific account ID.
    fun createAccount(accountName: String, accountId: UUID):
            CordaFuture<StateAndRef<AccountInfo>>

    // Returns all the keys used by the account specified by the account ID.
    fun accountKeys(accountId: UUID): List<PublicKey>

    // Returns the AccountInfo for an account name or account ID.
    fun accountInfo(accountId: UUID): StateAndRef<AccountInfo>?

    // Returns the AccountInfo for a given owning key
    fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>?

    // The assumption here is that Account names are unique at the node level but are not
    // guaranteed to be unique at the network level. The host Party can be used to
    // de-duplicate account names at the network level.
    fun accountInfo(accountName: String): StateAndRef<AccountInfo>?

    // Returns the Party which hosts the account specified by account ID.
    fun hostForAccount(accountId: UUID): Party?

    // Allows the account host to perform a vault query for the specified account ID.
    fun ownedByAccountVaultQuery(
            accountIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToAccountVaultQuery(
            accountIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun ownedByAccountVaultQuery(
            accountId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToAccountVaultQuery(
            accountId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    // Updates the account info with new account details. This may involve creating a
    // new account on another node with the new details. Once the new account has
    // been created, all the states can be moved to the new account.
//    fun moveAccount(currentInfo: StateAndRef<AccountInfo>, newInfo: AccountInfo)

    // De-activates the account.
//    fun deactivateAccount(accountId: UUID)

    // Sends AccountInfo specified by the account ID, to the specified Party. The
    // receiving Party will be able to access the AccountInfo from their AccountService.
    fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit>

    // Sends the StateAndRef of a given entity, e.g. AccountInfo with a particular account specified by the accountId
    fun <T : ContractState> broadcastStateToAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit>
}

@CordaService
class KeyManagementBackedAccountService(val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {


    @Suspendable
    override fun myAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy(AccountInfo::class.java)
                .states.filter { it.state.data.accountHost == services.myInfo.legalIdentities.first() }
    }

    @Suspendable
    override fun allAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy(AccountInfo::class.java).states
    }

    @Suspendable
    override fun createAccount(accountName: String): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(OpenNewAccountFlow(accountName))
    }

    @Suspendable
    override fun createAccount(accountName: String, accountId: UUID): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(OpenNewAccountFlow(accountName, accountId))
    }


    @Suspendable
    override fun accountKeys(accountId: UUID): List<PublicKey> {
        TODO("not implemented")
    }

    @Suspendable
    override fun accountInfo(accountId: UUID): StateAndRef<AccountInfo>? {
        return services.vaultService.queryBy(AccountInfo::class.java).states
                .filter { it.state.data.accountId == accountId }
                .map { it }.singleOrNull()
    }

    @Suspendable
    override fun accountInfo(accountName: String): StateAndRef<AccountInfo>? {
        return services.vaultService.queryBy(AccountInfo::class.java).states
                .filter { it.state.data.accountName == accountName }
                .map { it }.singleOrNull()
    }

    @Suspendable
    override fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>? {
        val uuid = services.withEntityManager {
            val query = createQuery("select ${PublicKeyHashToExternalId::externalId.name} from ${PublicKeyHashToExternalId::class.java.name} where ${PublicKeyHashToExternalId::publicKeyHash.name} = :hash", UUID::class.java)
            query.setParameter("hash", owningKey.toStringShort())
            query.singleResult
        }
        return accountInfo(uuid)
    }

    @Suspendable
    override fun hostForAccount(accountId: UUID): Party? {
        return accountInfo(accountId)?.state?.data?.accountHost
    }

    @Suspendable
    override fun ownedByAccountVaultQuery(accountIds: List<UUID>, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        val externalIDQuery = builder {
            VaultSchemaV1.StateToExternalId::externalId.`in`(accountIds)
        }
        val joinedQuery = queryCriteria.and(QueryCriteria.VaultCustomQueryCriteria(externalIDQuery, Vault.StateStatus.ALL))
        return services.vaultService.queryBy<ContractState>(joinedQuery).states
    }

    @Suspendable
    override fun ownedByAccountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        return ownedByAccountVaultQuery(listOf(accountId), queryCriteria)
    }

    @Suspendable
    override fun broadcastedToAccountVaultQuery(accountIds: List<UUID>, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        val externalIdQuery = builder {
            AllowedToSeeStateMapping::externalId.`in`(accountIds)
        }
        val joinedQuery = queryCriteria.and(QueryCriteria.VaultCustomQueryCriteria(externalIdQuery, Vault.StateStatus.ALL))
        return services.vaultService.queryBy<ContractState>(joinedQuery).states
    }

    @Suspendable
    override fun broadcastedToAccountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        return broadcastedToAccountVaultQuery(listOf(accountId), queryCriteria)
    }

    @Suspendable
    override fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareAccountInfoWithNodes(foundAccount, listOf(party)))
        } else {
            CompletableFuture<Unit>().also { it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node")) }.asCordaFuture()
        }
    }

    @Suspendable
    override fun <T : ContractState> broadcastStateToAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareStateWithAccountFlow(accountInfo = foundAccount.state.data, state = state))
        } else {
            CompletableFuture<Unit>().also { it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node")) }.asCordaFuture()
        }

    }

    @Suspendable
    inline fun <reified T : Any> flowAwareStartFlow(flowLogic: FlowLogic<T>): CordaFuture<T> {
        val currentFlow = FlowLogic.currentTopLevel
        return if (currentFlow != null) {
            val result = currentFlow.subFlow(flowLogic)
            doneFuture(result)
        } else {
            this.services.startFlow(flowLogic).returnValue
        }
    }

}