package net.corda.agent

import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class AgentAccountProvider(@Autowired val rpcConnection: NodeRPCConnection) {

    lateinit var agentAccount: StateAndRef<AccountInfo>

    @PostConstruct
    fun setupAccount() {
        val flowHandle = rpcConnection.proxy.startFlowDynamic(GetAccountsFlow::class.java, true)
        val accountsNow = flowHandle.returnValue.get()
        agentAccount = if (accountsNow.isEmpty()) {
            rpcConnection.proxy.startFlowDynamic(OpenNewAccountFlow::class.java, "AGENT_HOLDING_ACCOUNT").returnValue.get()
        } else {
            accountsNow.first()
        }
    }

}