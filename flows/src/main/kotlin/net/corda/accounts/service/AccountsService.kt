package net.corda.accounts.service

import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.getOrThrow
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.Table

interface AccountService : SerializeAsToken {

    // Accounts which the calling node hosts.
    fun myAccounts(): List<AccountInfo>

    // Returns all accounts, including those hosted by other nodes.
    fun allAccounts(): List<AccountInfo>

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

    // The assumption here is that Account names are unique at the node level but are not
    // guaranteed to be unique at the network level. The host Party can be used to
    // de-duplicate account names at the network level.
    fun accountInfo(accountName: String): StateAndRef<AccountInfo>?

    // Returns the Party which hosts the account specified by account ID.
    fun hostForAccount(accountId: UUID): Party

    // Allows the account host to perform a vault query for the specified account ID.
    fun accountVaultQuery(
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
    fun shareAccountInfoWithParty(accountId: UUID, party: Party)
}

data class SignedAccountInfo(val info: AccountInfo, val hostSig: DigitalSignature.WithKey)

@CordaSerializable
enum class AccountStatus {
    ACTIVE,
    INACTIVE
}

@BelongsToContract(AccountInfoContract::class)
@Table(name = "ACCOUNTS")
data class AccountInfo(
    @Column(name = "name", unique = false, nullable = false)
    val accountName: String,
    @Column(name = "host", unique = false, nullable = false)
    val accountHost: Party,
    @Id
    @Column(name = "id", unique = true, nullable = false)
    val accountId: UUID,
    override val linearId: UniqueIdentifier = UniqueIdentifier(accountName),
    @Column(name = "status")
    val status: AccountStatus = AccountStatus.ACTIVE,
    @Column(name = "signingKey")
    val signingKey: PublicKey
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(accountHost)
}

object AccountSchema : MappedSchema(AccountInfo::class.java, version = 1, mappedTypes = listOf(AccountInfo::class.java))

class AccountInfoContract : Contract {

    data class AccountCommands(private val step: String) : CommandData

    companion object {
        val OPEN = AccountCommands("OPEN")
        val MOVE_HOST = AccountCommands("MOVE_HOST")

    }

    override fun verify(tx: LedgerTransaction) {
        val accountCommand = tx.commands.requireSingleCommand(AccountCommands::class.java)
        if (accountCommand.value == OPEN) {
            require(tx.outputStates.size == 1) { "There should only ever be one output account state" }
            val accountInfo = tx.outputsOfType(AccountInfo::class.java).single()
            val requiredSigners = accountCommand.signers
            require(requiredSigners.size == 1) { "There should only be one required signer for opening an account " }
            require(requiredSigners.single() == accountInfo.accountHost.owningKey) { "Only the hosting node should be able to sign" }
        } else {
            throw NotImplementedError()
        }
    }

}


@CordaService
class KeyManagementBackedAccountService(val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {

    override fun myAccounts(): List<AccountInfo> {
        return services.vaultService.queryBy(AccountInfo::class.java)
            .states.filter { it.state.data.accountHost == services.myInfo.legalIdentities.first() }
            .map { it.state.data }
    }

    override fun allAccounts(): List<AccountInfo> {
        return services.vaultService.queryBy(AccountInfo::class.java).states.map { it.state.data }
    }

    override fun createAccount(accountName: String): CompletableFuture<StateAndRef<AccountInfo>> {
        return services.startFlow(OpenNewAccountFlow(accountName)).returnValue.toCompletableFuture()
    }

    override fun createAccount(accountName: String, accountId: UUID): StateAndRef<AccountInfo> {
        return services.startFlow(OpenNewAccountFlow(accountName, accountId)).returnValue.getOrThrow()
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

    override fun hostForAccount(accountId: UUID): Party {
        TODO("not implemented")
    }

    override fun accountVaultQuery(accountId: UUID, queryCriteria: QueryCriteria): List<StateAndRef<*>> {
        TODO("not implemented")
    }

    override fun moveAccount(currentInfo: StateAndRef<AccountInfo>, newInfo: AccountInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deactivateAccount(accountId: UUID) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shareAccountInfoWithParty(accountId: UUID, party: Party) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}