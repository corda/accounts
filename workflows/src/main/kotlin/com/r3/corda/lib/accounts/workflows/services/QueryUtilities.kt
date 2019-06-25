package com.r3.corda.lib.accounts.workflows.services

import com.r3.corda.lib.accounts.workflows.internal.accountQueryCriteria
import net.corda.core.contracts.ContractState
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import java.util.*

/** Helpers for querying the vault by account. */

fun <T : ContractState> VaultService.queryBy(accountIds: List<UUID>, contractStateType: Class<out T>): Vault.Page<T> {
    return _queryBy(accountQueryCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(accountQueryCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), paging, sorting, contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(accountQueryCriteria(accountIds), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(accountQueryCriteria(accountIds), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), paging, Sort(emptySet()), contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), sorting, contractStateType)
}

fun <T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        contractStateType: Class<out T>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), paging, sorting, contractStateType)
}

inline fun <reified T : ContractState> VaultService.queryBy(accountIds: List<UUID>): Vault.Page<T> {
    return _queryBy(accountQueryCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(accountQueryCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.queryBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): Vault.Page<T> {
    return _queryBy(criteria.and(accountQueryCriteria(accountIds)), paging, sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(accountIds: List<UUID>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(accountQueryCriteria(accountIds), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(accountQueryCriteria(accountIds), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultService.trackBy(
        accountIds: List<UUID>,
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort
): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria.and(accountQueryCriteria(accountIds)), paging, sorting, T::class.java)
}