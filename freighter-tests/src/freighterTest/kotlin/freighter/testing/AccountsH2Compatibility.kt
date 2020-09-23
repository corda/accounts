package freighter.testing

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.deployments.UnitOfDeployment
import freighter.installers.corda.CordaDistribution
import freighter.installers.corda.ENTERPRISE
import freighter.installers.corda.OPEN_SOURCE
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Test
import utility.getOrThrow

class AccountsH2Compatibility : DockerRemoteMachineBasedTest() {

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

    @Test
    fun `accounts can be loaded on a node running H2 on OS`() {
        val randomString = generateRandomString()
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val deploymentResult = SingleNodeDeployment(
            NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                .withCordapp(stressTesterCordapp)
                .withCordapp(accountsContracts)
                .withCordapp(accountsWorkflows)
                .withCordapp(modernCiV1)
                .withDatabase(machineProvider.requestDatabase(DeploymentMachineProvider.DatabaseType.H2))
        ).withVersion(UnitOfDeployment.DeploymentVersion("4.6-SNAPSHOT", true))
            .withDistribution(OPEN_SOURCE)
            .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachine
        nodeMachine.rpc {
            val createdAccount = startFlow(
                ::CreateAccount,
                "testAccount"
            ).returnValue.getOrThrow()
            println("Successfully created account: $createdAccount")
        }
    }


    @Test
    fun `accounts can be loaded on a node running H2 on ENT`() {
        val randomString = generateRandomString()
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val deploymentResult = SingleNodeDeployment(
            NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                .withCordapp(stressTesterCordapp)
                .withCordapp(accountsContracts)
                .withCordapp(accountsWorkflows)
                .withCordapp(modernCiV1)
                .withDatabase(machineProvider.requestDatabase(DeploymentMachineProvider.DatabaseType.H2))
        ).withVersion(UnitOfDeployment.DeploymentVersion("4.6-SNAPSHOT", true))
            .withDistribution(ENTERPRISE)
            .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachine
        nodeMachine.rpc {
            val createdAccount = startFlow(
                ::CreateAccount,
                "testAccount"
            ).returnValue.getOrThrow()
            println("Successfully created account: $createdAccount")
        }
    }
}