package freighter.testing

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.util.concurrent.CompletableFuture

class AccountsDBCompatibility : DockerRemoteMachineBasedTest() {

    val accountsContracts =
        NodeBuilder.DeployedCordapp.fromClassPath("accounts-contracts")

    val accountsWorkflows =
        NodeBuilder.DeployedCordapp.fromClassPath("accounts-workflows")

    val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
        group = "com.r3.corda.lib.ci",
        artifact = "ci-workflows",
        version = "1.0"
    )
    val stressTesterCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")

    lateinit var nms: CompletableFuture<DeploymentMachineProvider.NetworkServicesInfo>

    @BeforeEach
    fun setupNMS() {
        nms = machineProvider.generateNMSEnvironment()
    }

    @Test
    fun `accounts can be loaded on a node running postgres 9_6`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
    }

    @Test
    fun `accounts can be loaded on a node running postgres 10_10`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
    }

    @Test
    fun `accounts can be loaded on a node running postgres 11_5`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
    }

    @Test
    fun `accounts can be loaded on a node running ms_sql`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.MS_SQL)
    }

    @Test
    @OracleTest
    fun `accounts can be loaded on a node running oracle 12 r2`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.ORACLE_12_R2)
    }

    private fun runAccountsOnNodeRunningDatabase(db: DeploymentMachineProvider.DatabaseType) {
        val randomString = generateRandomString()

        val userName = System.getenv("ARTIFACTORY_USERNAME")
            ?: throw IllegalStateException("Please ensure that ARTIFACTORY_USERNAME is defined in the environment")
        val password = System.getenv("ARTIFACTORY_PASSWORD")
            ?: throw IllegalStateException("Please ensure that ARTIFACTORY_PASSWORD is defined in the environment")

        val deploymentContext = DeploymentContext(machineProvider, nms, userName, password)

        val deploymentResult = SingleNodeDeployment(
            NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                .withCordapp(stressTesterCordapp)
                .withCordapp(accountsContracts)
                .withCordapp(accountsWorkflows)
                .withCordapp(modernCiV1)
                .withDatabase(machineProvider.requestDatabase(db))
        ).withVersion("4.3")
            .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachines.single()

        nodeMachine.rpc {
            val createdAccount = startFlow(
                ::CreateAccount,
                "testAccount"
            ).returnValue.getOrThrow()
            println("Successfully created account: $createdAccount")
        }
    }

}