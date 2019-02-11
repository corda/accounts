package net.corda.fundadmin

import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.toBase58String
import net.corda.gold.trading.LoanBook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class FundAdministratorController(@Autowired private val rpcConnection: NodeRPCConnection) {

    @RequestMapping("/loans", method = [RequestMethod.GET])
    fun getAllLoansKnownAbout(): List<LoanBookView> {
        return getAllLoans().map { it.toLoanBookView() }
    }

    @RequestMapping("/me", method = [RequestMethod.GET])
    fun getMyParty(): String {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        return nodeParty.name.toString()
    }

    @RequestMapping("/accounts", method = [RequestMethod.GET])
    fun accountsKnownAndHosted(): List<AccountInfoView> {
        return getAllAccounts().map { it.toAccountView() }
    }


    data class AccountInfoView(
        val accountName: String,
        val accountHost: String,
        val accountId: UUID,
        val key: String?,
        val carbonCopyReivers: List<String> = listOf()
    )

    data class LoanBookView(val dealId: UUID, val valueInUSD: Long, val owningAccount: String? = null, val index: Int, val txHash: String)

    private fun getAllLoans(): List<StateAndRef<LoanBook>> {
        return rpcConnection.proxy.vaultQuery(LoanBook::class.java).states
    }

    private fun getAllAccounts() = rpcConnection.proxy.startFlowDynamic(GetAccountsFlow::class.java, false).returnValue.get()

}

private fun StateAndRef<AccountInfo>.toAccountView(): FundAdministratorController.AccountInfoView {
    val data = this.state.data
    return FundAdministratorController.AccountInfoView(
        data.accountName,
        data.accountHost.name.toString(),
        data.accountId,
        data.signingKey.toBase58String(),
        data.carbonCopyReivers.map { it.name.toString() })
}

private fun StateAndRef<LoanBook>.toLoanBookView(): FundAdministratorController.LoanBookView {
    val data = this.state.data
    return FundAdministratorController.LoanBookView(data.dealId, data.valueInUSD, data.owningAccount?.toBase58String(), this.ref.index, this.ref.txhash.toString())
}