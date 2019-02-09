package net.corda.agent

import net.corda.core.contracts.StateAndRef
import net.corda.gold.trading.GetAllLoansOwnedByAccountFlow
import net.corda.gold.trading.IssueLoanBookFlow
import net.corda.gold.trading.LoanBook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class AgentController(@Autowired private val rpcConnection: NodeRPCConnection, @Autowired private val agentAccountProvider: AgentAccountProvider) {

    @RequestMapping("/loans", method = [RequestMethod.GET])
    fun getLoans(): List<StateAndRef<LoanBook>> {
        return rpcConnection.proxy.startFlowDynamic(GetAllLoansOwnedByAccountFlow::class.java, agentAccountProvider.agentAccount).returnValue.get()
    }

    @RequestMapping("/createLoan", method = [RequestMethod.GET])
    fun createLoan(): StateAndRef<LoanBook> {
        return rpcConnection.proxy.startFlowDynamic(IssueLoanBookFlow::class.java, 10_000_000L, agentAccountProvider.agentAccount).returnValue.get()
    }


}