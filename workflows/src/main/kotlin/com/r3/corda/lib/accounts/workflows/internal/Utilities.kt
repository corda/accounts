package com.r3.corda.lib.accounts.workflows.internal

import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.PublicKeyHashToExternalId

// For writing less messy HQL.

/** Table names. */

internal val publicKeyHashToExternalId = PublicKeyHashToExternalId::class.java.name
internal val persistentKey = BasicHSMKeyManagementService.PersistentKey::class.java.name

/** Column names. */

internal val publicKeyHashToExternalId_externalId = PublicKeyHashToExternalId::externalId.name
internal val publicKeyHashToExternalId_publicKeyHash = PublicKeyHashToExternalId::publicKeyHash.name
internal val persistentKey_publicKeyHash = BasicHSMKeyManagementService.PersistentKey::publicKeyHash.name
internal val persistentKey_publicKey = BasicHSMKeyManagementService.PersistentKey::publicKey.name