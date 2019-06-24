package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import java.util.*

fun <V> CordaFuture<V>.runAndGet(network: MockNetwork): V {
    network.runNetwork()
    return this.getOrThrow()
}

fun StartedMockNode.identity(): Party {
    return this.info.legalIdentities.single()
}

val StateAndRef<AccountInfo>.id: UniqueIdentifier get() = state.data.linearId
val StateAndRef<AccountInfo>.uuid: UUID get() = state.data.linearId.id