package com.r3.corda.lib.accounts.workflows.services

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
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
     * Creates a new account by calling the [CreateAccount] flow. This flow returns a future which completes to return
     * a [StateAndRef] when the [CreateAccount] flow finishes. Note that account names must be unique at the host level,
     * therefore if a duplicate name is specified then the [CreateAccount] flow will throw an exception. This method
     * auto-generate an [AccountInfo.identifier] for the new account.
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

    fun <T : ContractState> shareStateWithAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit>

    fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: Party): CordaFuture<Unit>
}

class DuplicateAccountNameException : Exception()