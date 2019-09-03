package com.r3.corda.lib.accounts.workflows.services

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SerializeAsToken
import java.security.PublicKey
import java.util.*

/**
 * The [AccountService] provides an API for CorDapp developers to interact with accounts. An account makes no
 * assumptions about identity - instead, it is just a vault partition. Vault partitions are achieved by firstly
 * allocating UUIDs to [PublicKey]s (this is done via the [KeyManagementService] when a new key pair is created), then
 * secondly, when a state is stored in the vault using a [PublicKey] which is already mapped to a [UUID], the state can
 * be associated with the account identified by the mapped [UUID]. So, accounts are collections of [PublicKey]s mapped
 * to [UUID]s as well as the [ContractState]s participated in by those [PublicKey]s. Corda nodes act as account "hosts",
 * so we can say that an account is "hosted" by a particular [Party]. An account can only be hosted by one node at a
 * time. The [PublicKey]s used by accounts are randomly generated keys with no certificate path linking them to the host
 * node's legal identity. This means that only [AnonymousParty]s can be used to represent account keys. This also means
 * that there is no concept of identity for accounts at the network infrastructure level. Instead, CorDapp developers
 * must add their own identity layer for accounts. Account [UUID]s are unique at the network level where as account
 * names are unique at the node level. Also, the pair of account host and account name is unique at the network level.
 * Accounts are represented by a [ContractState] called [AccountInfo] - it contains the account name, account ID and
 * host. It may contain further information with future versions of the accounts module.
 *
 * The [AccountService] currently supports the following features:
 *
 * 1. Creating accounts with a specified name. [UUID]s are allocated randomly.
 * 2. Returning all accounts, our own accounts or accounts for a specific host.
 * 3. Returning all keys for a specified account ID.
 * 4. Returning the account ID associated with a specified [PublicKey].
 * 5. Returning the [AccountInfo] associated with a specified [PublicKey], account name or account ID.
 * 6. Sharing an [AccountInfo] with another [Party].
 * 7. Sharding a [StateAndRef] with another [Party] and synchronizing the [AccountInfo]s and [PublicKey]s associated
 *    with that state.
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
     * Returns all the keys associated with a specified account. These keys are [AnonymousParty]s which have been mapped
     * to a [Party] and [AccountInfo] via [IdentityService.registerKeyToMapping]. This API will only return keys which
     * have been generated on the calling node.
     *
     * @param id the [AccountInfo] to return a list of keys for.
     */
    fun accountKeys(id: UUID): List<PublicKey>

    /**
     * Returns the account ID associated with the specified [PublicKey] or null if the key is not mapped or known by
     * the node.
     *
     * @param owningKey the [PublicKey] to look-up
     */
    fun accountIdForKey(owningKey: PublicKey): UUID?

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