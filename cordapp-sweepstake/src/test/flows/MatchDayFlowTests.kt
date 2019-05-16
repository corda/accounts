package flows

import net.corda.accounts.flows.GetAccountInfo
import net.corda.accounts.flows.ReceiveStateForAccountFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class MatchDayFlowTests {

    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                listOf("net.corda.gold",
                        "net.corda.accounts.service",
                        "net.corda.accounts.contracts",
                        "net.corda.accounts.flows",
                        "net.corda.accounts.cordapp-sweepstake"), MockNetworkParameters(
                networkParameters = testNetworkParameters(
                        minimumPlatformVersion = 4
                )
        )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()

        a.registerInitiatedFlow(GetAccountInfo::class.java)
        b.registerInitiatedFlow(GetAccountInfo::class.java)
        a.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)
        b.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `match day outcome`(){
        //Verify one account is active one is inactive
    }
}