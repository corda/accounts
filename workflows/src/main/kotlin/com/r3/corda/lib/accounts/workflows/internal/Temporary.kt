package com.r3.corda.lib.accounts.workflows.internal

import com.r3.corda.lib.accounts.workflows.allowedToSeeCriteria
import net.corda.core.contracts.ContractState
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import java.util.*

// TODO: Delete these helper methods when https://r3-cev.atlassian.net/browse/CORDA-3038 is fixed.

fun <T : ContractState> VaultService.accountObservedQueryBy(accountIds: List<UUID>, contractStateType: Class<out T>): Vault.Page<T> {
    return _queryBy(allowedToSeeCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(allowedToSeeCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, sorting, contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(allowedToSeeCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(allowedToSeeCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, sorting, contractStateType)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(accountIds: List<UUID>): Vault.Page<T> {
    return _queryBy(allowedToSeeCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(allowedToSeeCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedQueryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(accountIds: List<UUID>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(allowedToSeeCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(allowedToSeeCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.accountObservedTrackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(allowedToSeeCriteria(accountIds)), paging, sorting, T::class.java)
}