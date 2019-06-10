package net.corda.accounts.workflows.test

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun <V> CordaFuture<V>.runAndGet(network: MockNetwork): V {
    network.runNetwork()
    return this.getOrThrow()
}

fun StartedMockNode.identity(): Party {
    return this.info.legalIdentities.single()
}