package net.corda.accounts.workflows.services

import net.corda.accounts.contracts.states.AccountInfo
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SerializeAsToken
import java.security.PublicKey
import java.util.*

@CordaService
interface AccountService : SerializeAsToken {

    /** Performs a vault query which returns all accounts hosted by the calling node. */
    fun ourAccounts(): List<StateAndRef<AccountInfo>>

    /** Performs a vault query and returns all the accounts hosted by the specified node. */
    fun accountsForHost(host: Party): List<StateAndRef<AccountInfo>>

    /** Performs a vault query and returns all accounts, including those hosted by other nodes. */
    fun allAccounts(): List<StateAndRef<AccountInfo>>

    /**
     * Creates a new account by calling the [CreateAccount] flow. This flow returns a future which completes to return
     * a [StateAndRef] when the [CreateAccount] flow finishes. Note that account names must be unique at the host level,
     * therefore if a duplicate name is specified then the [CreateAccount] flow will throw an exception.
     *
     * @param name the proposed name for this account.
     */
    fun createAccount(name: String): CordaFuture<StateAndRef<AccountInfo>>

    /**
     * Creates a new account by calling the [CreateAccount] flow. This flow returns a future which completes to return
     * a [StateAndRef] when the [CreateAccount] flow finishes. Note that account names must be unique at the host level,
     * therefore if a duplicate name is specified then the [CreateAccount] flow will throw an exception.
     *
     * @param name the proposed name for this account.
     * @param id the proposed account ID for this account.
     */
    fun createAccount(name: String, id: UUID): CordaFuture<StateAndRef<AccountInfo>>

    /**
     * Returns all the keys for a specified accountInfo. Note that this method only operates on accounts created by the
     * calling node, so calling this method with the [id] of an accountInfo created on another node will return an empty
     * list.
     *
     * @param id the accountInfo to return a list of keys for.
     */
    fun accountKeys(id: UUID): List<PublicKey>

    /**
     * Returns the [AccountInfo] for an accountInfo specified by [id]. This method will return accounts created on other
     * nodes if those [AccountInfo]s have previously been shared with the calling node.
     *
     * @param id the accountInfo id to return the [AccountInfo] for.
     */
    fun accountInfo(id: UUID): StateAndRef<AccountInfo>?

    /**
     * Returns the [AccountInfo] for an accountInfo specified by [owningKey]. Note that this method only operates on
     * accounts created by the calling node, so calling this method with the [id] of an accountInfo created on another node
     * will return an empty list.
     *
     * @param owningKey the public key to return an [AccountInfo] for.
     */
    fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>?

    /**
     * Returns the [AccountInfo] for an accountInfo specified by [name]. The assumption here is that Account names are
     * unique at the node level but are not guaranteed to be unique at the network level. The host [Party], stored in
     * the [AccountInfo] can be used to de-duplicate accountInfo names at the network level. Also, the accountInfo ID is
     * unique at the network level.
     *
     * @param name the accountInfo name to return an [AccountInfo] for.
     */
    fun accountInfo(name: String): StateAndRef<AccountInfo>?


    fun broadcastedToAccountVaultQuery(
            accountIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToAccountVaultQuery(
            accountId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit>

    fun <T : ContractState> broadcastStateToAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit>

    fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: AbstractParty): CordaFuture<Unit>
}

