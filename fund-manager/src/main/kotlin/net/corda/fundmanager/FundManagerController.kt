package net.corda.fundmanager

import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.flows.OpenNewAccountFlow
import net.corda.accounts.flows.ShareAccountInfoWithNodes
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toBase58String
import net.corda.gold.trading.LoanBook
import net.corda.gold.trading.MoveLoanBookToNewAccount
import net.corda.gold.trading.SplitLoanFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class ManagerController(@Autowired private val rpcConnection: NodeRPCConnection) {

    @RequestMapping("/loans", method = [RequestMethod.GET])
    fun getAllLoansForHostedAccounts(): List<LoanBookView> {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        val hostedAccounts = getAllAccounts().filter { it.state.data.accountHost == nodeParty }.map { it.state.data.signingKey }
        return getAllLoans().filter { it.state.data.owningAccount in hostedAccounts }.map { it.toLoanBookView() }
    }

    @RequestMapping("/me", method = [RequestMethod.GET])
    fun getMyParty(): String {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        return nodeParty.name.toString()
    }

    @RequestMapping("/accounts/hosted", method = [RequestMethod.GET])
    fun accountsKnownAndHosted(): List<AccountInfoView> {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        return getAllAccounts().filter { it.state.data.accountHost == nodeParty }.map { it.toAccountView() }
    }

    @RequestMapping("/accounts/all", method = [RequestMethod.GET])
    fun allKnownAccounts(): List<AccountInfoView> {
        return getAllAccounts().map{ it.toAccountView() }
    }

    @RequestMapping("/accounts/create/{accountName}/{administrator}", method = [RequestMethod.GET])
    fun createAccount(@PathVariable("accountName") accountName: String, @PathVariable("administrator") administrator: String): AccountInfoView {
        val administratorParty = rpcConnection.proxy.networkMapSnapshot().filter { it.legalIdentities.first().name.toString() == administrator }.single().legalIdentities.first()
        return rpcConnection.proxy.startFlowDynamic(OpenNewAccountFlow::class.java, accountName, listOf(administratorParty)).returnValue.get().toAccountView()
    }

    @RequestMapping("/accounts/share/{accountKey}/{party}", method = [RequestMethod.GET])
    fun shareAccount(@PathVariable("accountKey") accountKey: String, @PathVariable("party") party: String) {
        val partyToShareWith = rpcConnection.proxy.networkMapSnapshot().filter { it.legalIdentities.first().name.toString() == party }.single().legalIdentities.first()
        val accountToShare = getAllAccounts().filter { it.state.data.signingKey.toBase58String() == accountKey }.single()
        rpcConnection.proxy.startFlowDynamic(ShareAccountInfoWithNodes::class.java, accountToShare, listOf(partyToShareWith)).returnValue.getOrThrow()
    }

    @RequestMapping("/parties")
    fun allParties(): List<String> {
        return rpcConnection.proxy.networkMapSnapshot().map { it.legalIdentities.first().name.toString() }
    }

    @RequestMapping("/loan/split/{txHash}/{txIdx}", method = [RequestMethod.GET])
    fun splitLoan(@PathVariable("txHash") txHash: String, @PathVariable("txIdx") txIdx: Int): List<LoanBookView> {
        val loanToSplit = getAllLoans().filter { it.ref.txhash.toString() == txHash }.filter { it.ref.index == txIdx }.single()
        val resultOfSplit = rpcConnection.proxy.startFlowDynamic(SplitLoanFlow::class.java, loanToSplit, loanToSplit.state.data.valueInUSD / 2).returnValue.get()
        return getAllLoans().map { it.toLoanBookView() }
    }

    @RequestMapping("/loan/move/{txHash}/{txIdx}/{accountKey}", method = [RequestMethod.GET])
    fun moveLoan(@PathVariable("txHash") txHash: String, @PathVariable("txIdx") txIdx: Int, @PathVariable("accountKey") accountKey: String): List<LoanBookView> {
        val loanToMove = getAllLoans().filter { it.ref.txhash.toString() == txHash }.single { it.ref.index == txIdx }
        val accountToMoveInto = getAllAccounts().single { it.state.data.signingKey.toBase58String() == accountKey }
        val resultOfMove = rpcConnection.proxy.startFlowDynamic(MoveLoanBookToNewAccount::class.java, accountToMoveInto.state.data.accountId, loanToMove).returnValue.get()
        return getAllLoans().map { it.toLoanBookView() }
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

private fun StateAndRef<AccountInfo>.toAccountView(): ManagerController.AccountInfoView {
    val data = this.state.data
    return ManagerController.AccountInfoView(
        data.accountName,
        data.accountHost.name.toString(),
        data.accountId,
        data.signingKey.toBase58String(),
        data.carbonCopyReivers.map { it.name.toString() })
}

private fun StateAndRef<LoanBook>.toLoanBookView(): ManagerController.LoanBookView {
    val data = this.state.data
    return ManagerController.LoanBookView(data.dealId, data.valueInUSD, data.owningAccount?.toBase58String(), this.ref.index, this.ref.txhash.toString())
}