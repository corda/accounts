package net.corda.gold

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.GoldBrick
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic

class MineBrickFlow(val accountToMineTo: String) : FlowLogic<StateAndRef<GoldBrick>?>() {

    @Suspendable
    override fun call(): StateAndRef<GoldBrick>? {


//        serviceHub.


        return null

    }

}