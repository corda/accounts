package com.r3.corda.lib.accounts.workflows.internal

import com.r3.corda.lib.accounts.workflows.services.AccountService
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.ServiceHub

// For writing less messy HQL.

/** Table names. */

internal val publicKeyHashToExternalId = "net.corda.node.services.persistence.PublicKeyHashToExternalId"
internal val persistentKey = "net.corda.node.services.keys.BasicHSMKeyManagementService\$PersistentKey"

internal val publicKeyHashToAccountId = "com.r3.corda.lib.accounts.workflows.internal.schemas.PublicKeyHashToAccountIdMapping"

/** Helper for obtaining a [AccountService]. */
@VisibleForTesting
val ServiceHub.accountService: AccountService
    get() = cordaService(KeyManagementBackedAccountService::class.java)