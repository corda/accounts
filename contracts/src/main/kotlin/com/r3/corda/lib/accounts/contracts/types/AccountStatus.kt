package com.r3.corda.lib.accounts.contracts.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class AccountStatus {
    ACTIVE,
    INACTIVE
}