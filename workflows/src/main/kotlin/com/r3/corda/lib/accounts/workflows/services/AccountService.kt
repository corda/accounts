package com.r3.corda.lib.accounts.workflows.services

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.externalIdCriteria
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.messaging.DataFeed
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.SerializeAsToken
import java.security.PublicKey
import java.util.*

/**
 * The [AccountService] intends to
 */
@CordaService
interface AccountService : SerializeAsToken {

    /** Performs a vault query which returns all accounts hosted by the calling node. */
    fun ourAccounts(): List<StateAndRef<AccountInfo>>

    /** Performs a vault query and returns all the accounts hosted by the specified node. */
    fun accountsForHost(host: Party): List<StateAndRef<AccountInfo>>

    /** Performs a vault query and returns all accounts, including those hosted by other nodes. */
    fun allAccounts(): List<StateAndRef<AccountInfo>>

    /**
     * Creates a new account by calling the [CreateAccount] flow. This method returns a future which completes to return
     * a [StateAndRef] when the [CreateAccount] flow finishes. Note that account names must be unique at the host level,
     * therefore if a duplicate name is specified then the [CreateAccount] flow will throw an exception. This method
     * auto-generate an [AccountInfo.identifier] for the new account.
     *
     * @param name the proposed name for this account.
     */
    fun createAccount(name: String): CordaFuture<StateAndRef<AccountInfo>>

    /**
     * Creates a new account by calling the [CreateAccount] flow. This method returns a future which completes to return
     * a [StateAndRef] when the [CreateAccount] flow finishes. Note that account names must be unique at the host level,
     * therefore if a duplicate name is specified then the [CreateAccount] flow will throw an exception.
     *
     * @param name the proposed name for this account.
     * @param id the proposed account ID for this account.
     */
    fun createAccount(name: String, id: UUID): CordaFuture<StateAndRef<AccountInfo>>

    /**
     * Returns all the keys associated with a specified account. These keys are [AnonymousParty]s which have been mapped
     * to a [Party] and [AccountInfo] via [IdentityService.registerKeyToMapping].
     *
     * @param id the [AccountInfo] to return a list of keys for.
     */
    fun accountKeys(id: UUID): List<PublicKey>

    /**
     * Returns the [AccountInfo] for an account specified by [id]. This method will return accounts created on other
     * nodes as well as accounts created on the calling node. Accounts created on other nodes will only be returned if
     * they have been shared with your node, via the share account info flows.
     *
     * @param id the account ID to return the [AccountInfo] for.
     */
    fun accountInfo(id: UUID): StateAndRef<AccountInfo>?

    /**
     * Returns the [AccountInfo] for an account specified by [owningKey]. This method will return accounts created on
     * other nodes as well as accounts created on the calling node. Accounts created on other nodes will only be
     * returned if they have been shared with your node, via the share account info flows.
     *
     * @param owningKey the public key to return an [AccountInfo] for.
     */
    fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>?

    /**
     * Returns the [AccountInfo]s for an accounts specified by [name]. The assumption here is that Account names are
     * unique at the node level but are not guaranteed to be unique at the network level. The host [Party], stored in
     * the [AccountInfo] can be used to de-duplicate accountInfo names at the network level. Also, the accountInfo ID is
     * unique at the network level.
     *
     * This method may return more than one [AccountInfo].
     *
     * @param name the account name to return [AccountInfo]s for.
     */
    fun accountInfo(name: String): List<StateAndRef<AccountInfo>>?

    /**
     * Shares an [AccountInfo] [StateAndRef] with the specified [Party]. The [AccountInfo]is always stored by the
     * recipient using [StatesToRecord.ALL_VISIBLE].
     *
     * @param accountId the account ID of the [AccountInfo] to share.
     * @param party the [Party] to share the [AccountInfo] with.
     */
    fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit>

    /**
     * This flow shares a [StateAndRef] with an account as opposed to a full node. See the documentation for
     * [ShareStateWithAccountFlow] for further information. This method returns a future which completes to return the
     * result of the [ShareStateWithAccount] flow.
     *
     * @param accountId the account to share the [StateAndRef] with
     * @param state the [StateAndRef] to share
     */
    fun <T : ContractState> shareStateWithAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit>

    fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: Party): CordaFuture<Unit>
}

/** Helpers for querying the vault by account. */

fun <T : ContractState> VaultService.queryBy(accountIds: List<UUID>, contractStateType: Class<out T>): Vault.Page<T> {
    return _queryBy(externalIdCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(externalIdCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), paging, sorting, contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(externalIdCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(externalIdCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), paging, sorting, contractStateType)
}

// Helpers using reified generics which are nicer to use with Kotlin.

inline fun <reified T : ContractState> VaultService.queryBy(accountIds: List<UUID>): Vault.Page<T> {
    return _queryBy(externalIdCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(externalIdCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(externalIdCriteria(accountIds)), paging, sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(accountIds: List<UUID>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(externalIdCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(externalIdCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(externalIdCriteria(accountIds)), paging, sorting, T::class.java)
}