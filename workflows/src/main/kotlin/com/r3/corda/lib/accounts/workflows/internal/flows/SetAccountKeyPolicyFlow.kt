package com.r3.corda.lib.accounts.workflows.internal.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import java.util.*

// TODO: Finish these flows when we release the Corda 5 version of accounts.

@StartableByService
@StartableByRPC
class SetAccountKeyPolicyFlow(val accountId: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        TODO("not available in V4 Corda") //To change body of created functions use File | Settings | File Templates.
    }

}

@StartableByService
@StartableByRPC
class GetAccountKeyPolicyFlow(val accountId: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        TODO("not available in V4 Corda") //To change body of created functions use File | Settings | File Templates.
    }
}

enum class AccountKeyPolicy {
    REUSE,
    FRESH
}