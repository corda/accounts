package net.corda.agent

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.OurAccounts
import net.corda.core.contracts.StateAndRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class AgentAccountProvider(@Autowired val rpcConnection: NodeRPCConnection) {

    lateinit var agentAccount: StateAndRef<AccountInfo>

    @PostConstruct
    fun setupAccount() {
        val flowHandle = rpcConnection.proxy.startFlowDynamic(OurAccounts::class.java, true)
        val accountsNow = flowHandle.returnValue.get()
        agentAccount = if (accountsNow.isEmpty()) {
            rpcConnection.proxy.startFlowDynamic(CreateAccount::class.java, "AGENT_HOLDING_ACCOUNT").returnValue.get()
        } else {
            accountsNow.first()
        }
    }

}