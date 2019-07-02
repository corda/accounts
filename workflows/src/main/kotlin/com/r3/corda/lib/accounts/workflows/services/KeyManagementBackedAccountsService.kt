package com.r3.corda.lib.accounts.workflows.services

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.*
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts
import com.r3.corda.lib.accounts.workflows.flows.ShareStateWithAccount
import com.r3.corda.lib.accounts.workflows.internal.publicKeyHashToExternalId
import com.r3.corda.lib.accounts.workflows.internal.publicKeyHashToExternalId_externalId
import com.r3.corda.lib.accounts.workflows.internal.publicKeyHashToExternalId_publicKeyHash
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture

@CordaService
class KeyManagementBackedAccountService(val services: AppServiceHub) : AccountService, SingletonSerializeAsToken() {

    @Suspendable
    override fun accountsForHost(host: Party): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(accountHostCriteria(host))).states
    }

    @Suspendable
    override fun ourAccounts(): List<StateAndRef<AccountInfo>> {
        return accountsForHost(services.ourIdentity)
    }

    @Suspendable
    override fun allAccounts(): List<StateAndRef<AccountInfo>> {
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria).states
    }

    @Suspendable
    override fun accountInfo(id: UUID): StateAndRef<AccountInfo>? {
        val uuidCriteria = accountUUIDCriteria(id)
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(uuidCriteria)).states.singleOrNull()
    }

    @Suspendable
    override fun accountInfo(name: String): StateAndRef<AccountInfo>? {
        val nameCriteria = accountNameCriteria(name)
        return services.vaultService.queryBy<AccountInfo>(accountBaseCriteria.and(nameCriteria)).states.singleOrNull()
    }

    @Suspendable
    override fun createAccount(name: String): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(CreateAccount(name))
    }

    @Suspendable
    override fun createAccount(name: String, id: UUID): CordaFuture<StateAndRef<AccountInfo>> {
        return flowAwareStartFlow(CreateAccount(name, id))
    }

    override fun <T : StateAndRef<*>> shareStateAndSyncAccounts(state: T, party: Party): CordaFuture<Unit> {
        return flowAwareStartFlow(ShareStateAndSyncAccounts(state, party))
    }

    @Suspendable
    override fun accountKeys(id: UUID): List<PublicKey> {
        throw UnsupportedOperationException("It is not possible to lookup existing keys for an account on Corda 4 " +
                "please upgrade to Corda 5 or perform the query in SQL using ServiceHub.jdbcConnection.")
        // TODO once the join column is introduced - use the following
//        return services.withEntityManager {
//            val query = createQuery(
//                    "select a.${PersistentIdentityService.PersistentIdentity::identity.name} from \n" +
//                            "${PersistentIdentityService.PersistentIdentity::class.java.name} a, ${PublicKeyHashToExternalId::class.java.name} b \n" +
//                            "where \n" +
//                            "   b.${PublicKeyHashToExternalId::externalId.name} = :uuid \n" +
//                            " and \n" +
//                            "   b.${PublicKeyHashToExternalId::publicKeyHash.name} = a.${PersistentIdentityService.PersistentIdentity::publicKeyHash.name}", ByteArray::class.java)
//
//            query.setParameter("uuid", accountId)
//            query.resultList.map { PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(it.inputStream())) }.map { it.owningKey }
//        }
    }

    @Suspendable
    override fun accountInfo(owningKey: PublicKey): StateAndRef<AccountInfo>? {
        val uuid = services.withEntityManager {
            val query = createQuery(
                    """
                        select $publicKeyHashToExternalId_externalId
                        from $publicKeyHashToExternalId
                        where $publicKeyHashToExternalId_publicKeyHash = :hash
                    """,
                    UUID::class.java
            )
            query.setParameter("hash", owningKey.toStringShort())
            query.resultList
        }
        return uuid.singleOrNull()?.let { accountInfo(it) }
    }

    @Suspendable
    override fun shareAccountInfoWithParty(accountId: UUID, party: Party): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareAccountInfo(foundAccount, listOf(party)))
        } else {
            CompletableFuture<Unit>().also {
                it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node"))
            }.asCordaFuture()
        }
    }

    @Suspendable
    override fun <T : ContractState> shareStateWithAccount(accountId: UUID, state: StateAndRef<T>): CordaFuture<Unit> {
        val foundAccount = accountInfo(accountId)
        return if (foundAccount != null) {
            flowAwareStartFlow(ShareStateWithAccount(accountInfo = foundAccount.state.data, state = state))
        } else {
            CompletableFuture<Unit>().also {
                it.completeExceptionally(IllegalStateException("Account: $accountId was not found on this node"))
            }.asCordaFuture()
        }

    }

    @Suspendable
    private inline fun <reified T : Any> flowAwareStartFlow(flowLogic: FlowLogic<T>): CordaFuture<T> {
        val currentFlow = FlowLogic.currentTopLevel
        return if (currentFlow != null) {
            val result = currentFlow.subFlow(flowLogic)
            doneFuture(result)
        } else {
            this.services.startFlow(flowLogic).returnValue
        }
    }
}