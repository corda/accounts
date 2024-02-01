package net.corda.fundmanager

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.AllAccounts
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toBase58String
import net.corda.gold.trading.contracts.states.LoanBook
import net.corda.gold.trading.workflows.flows.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletRequest

const val USER_ATTRIBUTE: String = "USER"


@RestController
class ManagerController(@Autowired private val rpcConnection: NodeRPCConnection) {

    @RequestMapping("/loans", method = [RequestMethod.GET])
    fun getAllLoansForHostedAccounts(request: HttpServletRequest): List<LoanBookView> {
        val contextUser = validateSessionUser(request)
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        val hostedAccounts = getAllAccounts().filter { it.state.data.host == nodeParty }.map { it.state.data.identifier.id }
        return getAllLoans(contextUser).filter { UUID.fromString(it.state.data.owningAccount?.toBase58String()) in hostedAccounts }.map { it.toLoanBookView() }
    }

    private fun validateSessionUser(request: HttpServletRequest): String {
        val session = request.getSession(true)
        val contextUser = session.getAttribute(USER_ATTRIBUTE) ?: "AdminUser"
        return contextUser as String
    }

    @RequestMapping("/me", method = [RequestMethod.GET])
    fun getMyParty(): String {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        return nodeParty.name.toString()
    }

    @RequestMapping("/accounts/hosted", method = [RequestMethod.GET])
    fun accountsKnownAndHosted(): List<AccountInfoView> {
        val nodeParty = rpcConnection.proxy.nodeInfo().legalIdentities.first()
        return getAllAccounts().filter { it.state.data.host == nodeParty }.map { it.toAccountView() }
    }

    @RequestMapping("/accounts/all", method = [RequestMethod.GET])
    fun allKnownAccounts(): List<AccountInfoView> {
        return getAllAccounts().map { it.toAccountView() }
    }

    @RequestMapping("/accounts/create/{accountName}/{administratorAccountKey}", method = [RequestMethod.GET])
    fun createAccount(@PathVariable("accountName") accountName: String, @PathVariable("administratorAccountKey") administratorAccountKey: String?): AccountInfoView {
        val adminAccountList = if (administratorAccountKey == "null") {
            listOf()
        } else {
            listOf(getAllAccounts().single { it.state.data.identifier.id.toString() == administratorAccountKey }.state.data)
        }
        val openedAccount = rpcConnection.proxy.startFlow(::CreateAccount, accountName).returnValue.get()
        rpcConnection.proxy.startFlow(::AddBroadcastAccountToAccountFlow, openedAccount.state.data.identifier.id, adminAccountList.first().identifier.id, true).returnValue.getOrThrow()
        return openedAccount.toAccountView()
    }

    @RequestMapping("/accounts/share/{accountKey}/{party}", method = [RequestMethod.GET])
    fun shareAccount(@PathVariable("accountKey") accountKey: String, @PathVariable("party") party: String) {
        val partyToShareWith = rpcConnection.proxy.networkMapSnapshot().filter { it.legalIdentities.first().name.toString() == party }.single().legalIdentities.first()
        val accountToShare = getAllAccounts().single { it.state.data.identifier.id.toString() == accountKey }
        rpcConnection.proxy.startFlowDynamic(ShareAccountInfo::class.java, accountToShare, listOf(partyToShareWith)).returnValue.getOrThrow()
    }

    @RequestMapping("/parties")
    fun allParties(): List<String> {
        return rpcConnection.proxy.networkMapSnapshot().map { it.legalIdentities.first().name.toString() }
    }

    @RequestMapping("/loan/split/{txHash}/{txIdx}", method = [RequestMethod.GET])
    fun splitLoan(@PathVariable("txHash") txHash: String, @PathVariable("txIdx") txIdx: Int, request: HttpServletRequest): List<LoanBookView> {
        val contextUser = validateSessionUser(request)
        val loanToSplit = getAllLoans(contextUser).filter { it.ref.txhash.toString() == txHash }.filter { it.ref.index == txIdx }.single()
        rpcConnection.proxy.startFlowDynamic(SplitLoanFlow::class.java, loanToSplit, loanToSplit.state.data.valueInUSD / 2).returnValue.get()
        return getAllLoans(contextUser).map { it.toLoanBookView() }
    }

    @RequestMapping("/loan/move/{txHash}/{txIdx}/{accountKey}", method = [RequestMethod.GET])
    fun moveLoan(@PathVariable("txHash") txHash: String, @PathVariable("txIdx") txIdx: Int, @PathVariable("accountKey") accountKey: String, request: HttpServletRequest): List<LoanBookView> {
        val contextUser = validateSessionUser(request)
        val loanToMove = getAllLoans(contextUser).filter { it.ref.txhash.toString() == txHash }.single { it.ref.index == txIdx }
        val accountToMoveInto = getAllAccounts().single { it.state.data.identifier.id.toString() == accountKey }
        rpcConnection.proxy.startFlowDynamic(MoveLoanBookToNewAccount::class.java, accountToMoveInto.state.data.identifier.id, loanToMove).returnValue.get()
        return getAllLoans(contextUser).map { it.toLoanBookView() }
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

    @Suppress("UNUSED_PARAMETER")
    @RequestMapping("/users/permission/{userName}/{accountKey}", method = [RequestMethod.GET])
    fun permissionUserToAccount(@PathVariable("userName") userName: String, @PathVariable("accountKey") accountKey: String, request: HttpServletRequest): String {
        val accountToUse = getAllAccounts().single { it.state.data.identifier.id.toString() == accountKey }
        rpcConnection.proxy.startFlow(::PermissionWebLoginToAccountFlow, userName, accountToUse.state.data.identifier.id, true).returnValue.getOrThrow()
        return "OK"
    }

    @RequestMapping("/users/current", method = [RequestMethod.GET])
    fun getCurrentUser(request: HttpServletRequest): String {
        return validateSessionUser(request)
    }

    data class AccountInfoView(
            val accountName: String,
            val accountHost: String,
            val accountId: UUID
    )

    data class LoanBookView(val dealId: UUID, val valueInUSD: Long, val owningAccount: String? = null, val index: Int, val txHash: String)

    private fun getAllLoans(contextUser: String): List<StateAndRef<LoanBook>> {
        return rpcConnection.proxy.startFlow(::GetWebUserFlow, contextUser).returnValue.toCompletableFuture().thenCompose { user ->
            rpcConnection.proxy.startFlow(::GetAllLoansFlow, user?.permissionedAccounts).returnValue.toCompletableFuture()
        }.getOrThrow()
    }

    private fun getAllAccounts() = rpcConnection.proxy.startFlowDynamic(AllAccounts::class.java, false).returnValue.get()

}

private fun StateAndRef<AccountInfo>.toAccountView(): ManagerController.AccountInfoView {
    val data = this.state.data
    return ManagerController.AccountInfoView(
            data.name,
            data.host.name.toString(),
            data.identifier.id
    )
}

private fun StateAndRef<LoanBook>.toLoanBookView(): ManagerController.LoanBookView {
    val data = this.state.data
    return ManagerController.LoanBookView(data.dealId, data.valueInUSD, data.owningAccount?.toBase58String(), this.ref.index, this.ref.txhash.toString())
}


@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Not Logged in")
class UnAuthorisedException : RuntimeException()