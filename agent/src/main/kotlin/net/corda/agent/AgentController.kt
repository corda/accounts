package net.corda.agent

import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toBase58String
import net.corda.gold.trading.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletRequest

const val USER_ATTRIBUTE: String = "USER"

@RestController
class AgentController(@Autowired private val rpcConnection: NodeRPCConnection, @Autowired private val agentAccountProvider: AgentAccountProvider) {

    @RequestMapping("/loans", method = [RequestMethod.GET])
    fun getLoans(): List<LoanBookView> {
        return getAllLoans().map { it.toLoanBookView() }
    }

    private fun getAllLoans() = rpcConnection.proxy.startFlowDynamic(GetAllLoansOwnedByAccountFlow::class.java, agentAccountProvider.agentAccount).returnValue.get()

    @RequestMapping("/createLoan", method = [RequestMethod.GET])
    fun createLoan(): LoanBookView {
        return rpcConnection.proxy.startFlowDynamic(IssueLoanBookFlow::class.java, 10_000_000L, agentAccountProvider.agentAccount).returnValue.get()
            .let { it.toLoanBookView() }
    }

    @RequestMapping("/accounts", method = [RequestMethod.GET])
    fun accountsKnown(): List<AccountInfoView> {
        return getAllAccounts().map { it.toAccountView() }
    }

    private fun getAllAccounts() = rpcConnection.proxy.startFlowDynamic(GetAccountsFlow::class.java, false).returnValue.get()

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

    @RequestMapping("/users/all", method = [RequestMethod.GET])
    fun getAllUsers(): List<String> {
        return rpcConnection.proxy.startFlowDynamic(GetAllWebUsersFlow::class.java).returnValue.getOrThrow().sortedBy { it }
    }

    @RequestMapping("/users/create/{userName}", method = [RequestMethod.GET])
    fun createUser(@PathVariable("userName") userName: String): String {
        return rpcConnection.proxy.startFlow(::NewWebAccountFlow, userName).returnValue.getOrThrow().webAccount!!
    }


    @RequestMapping("/users/login/{userName}", method = [RequestMethod.GET])
    fun loginAsUser(@PathVariable("userName") userName: String, request: HttpServletRequest): String {
        val sessionToUse = request.getSession(true)
        sessionToUse.setAttribute(USER_ATTRIBUTE, userName)
        return "OK"
    }

    @RequestMapping("/users/permission/{userName}/{accountKey}", method = [RequestMethod.GET])
    fun permissionUserToAccount(@PathVariable("userName") userName: String, @PathVariable("accountKey") accountKey: String, request: HttpServletRequest): String {
        val accountToUse = getAllAccounts().single { it.state.data.signingKey.toBase58String() == accountKey }
        rpcConnection.proxy.startFlow(::PermissionWebLoginToAccountFlow, userName, accountToUse.state.data.accountId, true).returnValue.getOrThrow()
        return "OK"
    }

    @RequestMapping("/users/current", method = [RequestMethod.GET])
    fun getCurrentUser(request: HttpServletRequest): String {
        return validateSessionUser(request)
    }

    private fun validateSessionUser(request: HttpServletRequest): String {
        val session = request.getSession(true)
        val contextUser = session.getAttribute(USER_ATTRIBUTE) ?: throw UnAuthorisedException()
        return contextUser as String
    }


    data class AccountInfoView(
        val accountName: String,
        val accountHost: String,
        val accountId: UUID,
        val key: String?
    )

    data class LoanBookView(val dealId: UUID, val valueInUSD: Long, val owningAccount: String? = null, val index: Int, val txHash: String)

}

private fun StateAndRef<AccountInfo>.toAccountView(): AgentController.AccountInfoView {
    return this.state.data.toAccountView()
}

private fun AccountInfo.toAccountView(): AgentController.AccountInfoView {
    return AgentController.AccountInfoView(
        this.accountName,
        this.accountHost.name.toString(),
        this.accountId,
        this.signingKey.toBase58String()
    )
}

private fun StateAndRef<LoanBook>.toLoanBookView(): AgentController.LoanBookView {
    val data = this.state.data
    return AgentController.LoanBookView(data.dealId, data.valueInUSD, data.owningAccount?.toBase58String(), this.ref.index, this.ref.txhash.toString())
}

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Not Logged in")
class UnAuthorisedException : RuntimeException()