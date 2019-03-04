package net.corda.accounts.service

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ShareAccountInfoWithNodes
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
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
    fun createAccount(accountName: String): CompletableFuture<StateAndRef<AccountInfo>>

    // Overload for creating an account with a specific account ID.
    fun createAccount(accountName: String, accountId: UUID):
            StateAndRef<AccountInfo>

    // Creates a new KeyPair, links it to the account and returns the publickey.
    fun freshKeyForAccount(accountId: UUID): AnonymousParty

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
    fun moveAccount(currentInfo: StateAndRef<AccountInfo>, newInfo: AccountInfo)

    // De-activates the account.
    fun deactivateAccount(accountId: UUID)

    // Sends AccountInfo specified by the account ID, to the specified Party. The
    // receiving Party will be able to access the AccountInfo from their AccountService.
    fun shareAccountInfoWithParty(accountId: UUID, party: Party): CompletableFuture<Boolean>

    fun <T : ContractState> broadcastStateToAccount(accountId: UUID, state: StateAndRef<T>): CompletableFuture<StateAndRef<T>>
}

@CordaService
class KeyManagementBackedAccountService(private val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {



    override fun myAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy(AccountInfo::class.java)
            .states.filter { it.state.data.accountHost == services.myInfo.legalIdentities.first() }
    }

    override fun allAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy(AccountInfo::class.java).states
    }

    override fun createAccount(accountName: String): CompletableFuture<StateAndRef<AccountInfo>> {
        return services.startFlow(OpenNewAccountFlow(accountName)).returnValue.toCompletableFuture()
    }

    override fun createAccount(accountName: String, accountId: UUID): StateAndRef<AccountInfo> {
        return services.startFlow(OpenNewAccountFlow(accountName, accountId)).returnValue.getOrThrow()
    }

    fun createAccount(accountName: String, carbonCopyReceivers: List<Party>): CompletableFuture<StateAndRef<AccountInfo>> {
        return services.startFlow(OpenNewAccountFlow(accountName, carbonCopyReceivers)).returnValue.toCompletableFuture()
    }

    override fun freshKeyForAccount(accountId: UUID): AnonymousParty {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun accountKeys(accountId: UUID): List<PublicKey> {
        return services.vaultService.queryBy(AccountInfo::class.java).states
            .filter { it.state.data.accountId == accountId }
            .map { it.state.data.signingKey }
    }

    override fun accountInfo(accountId: UUID): StateAndRef<AccountInfo>? {
        return services.vaultService.queryBy(AccountInfo::class.java).states
            .filter { it.state.data.accountId == accountId }
            .map { it }.singleOrNull()
    }

    override fun accountInfo(accountName: String): StateAndRef<AccountInfo>? {
        return services.vaultService.queryBy(AccountInfo::class.java).states
            .filter { it.state.data.accountName == accountName }
            .map { it }.singleOrNull()
    }

    override fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>? {
        return services.vaultService.queryBy(AccountInfo::class.java).states
            .filter { it.state.data.signingKey == owningKey }
            .map { it }.singleOrNull()
    }

    override fun hostForAccount(accountId: UUID): Party? {
        return accountInfo(accountId)?.state?.data?.accountHost
    }

    override fun ownedByAccountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        val externalIDQuery = builder {
            VaultSchemaV1.StateToExternalId::externalId.equal(accountId)
        }
        val joinedQuery = queryCriteria.and(QueryCriteria.VaultCustomQueryCriteria(externalIDQuery, Vault.StateStatus.ALL))
        return services.vaultService.queryBy<ContractState>(joinedQuery).states
    }

    override fun broadcastedToAccountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        TODO("not implemented")
    }

    override fun moveAccount(currentInfo: StateAndRef<AccountInfo>, newInfo: AccountInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deactivateAccount(accountId: UUID) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shareAccountInfoWithParty(accountId: UUID, party: Party): CompletableFuture<Boolean> {
        accountInfo(accountId)?.let {
            return services.startFlow(ShareAccountInfoWithNodes(it, listOf(party))).returnValue.toCompletableFuture()
        }
        return CompletableFuture.completedFuture(false)
    }

    override fun <T : ContractState> broadcastStateToAccount(accountId: UUID, state: StateAndRef<T>): CompletableFuture<StateAndRef<T>> {
        TODO("not implemented")
    }

}