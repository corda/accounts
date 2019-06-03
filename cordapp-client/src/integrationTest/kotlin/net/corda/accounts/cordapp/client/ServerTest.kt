package net.corda.accounts.cordapp.client

import net.corda.accounts.cordapp.client.webserver.Application
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import spring.springDriver

class ServerTest {

    companion object {
        private val log = contextLogger()
        private val partyA = CordaX500Name("PartyA", "LONDON", "GB")
    }

    private val rpcUsers = listOf(User("user1", "password", setOf("ALL")))

    //TODO Needs to be completed
    @Test
    fun `run server test`() {
        springDriver(DriverParameters(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, rpcUsers = rpcUsers)),
                extraCordappPackagesToScan = listOf("com.gitcoins")
        )) {
            val (notary, nodeA) = listOf(
                    defaultNotaryNode,
                    startNode(providedName = partyA, rpcUsers = rpcUsers)
            ).map { it.getOrThrow() }

            log.info("All nodes started")

            val (notaryAddr, nodeAAddr) = listOf(notary, nodeA).map {
                startSpringBootWebapp(Application::class.java, it, "/api/git/create-key")
            }.map { it.getOrThrow().listenAddress }
        }
    }
}