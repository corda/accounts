package com.r3.corda.lib.accounts.contracts.types

import net.corda.core.serialization.CordaSerializable

/**
 * For use in later versions of accounts.
 */
@CordaSerializable
enum class AccountStatus {
    ACTIVE,
    INACTIVE
}