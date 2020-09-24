package freighter.testing

import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByUUID
import com.r3.corda.lib.accounts.workflows.flows.CordappVersionDetector
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.deployments.UnitOfDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.util.concurrent.CompletableFuture

class AccountsUpgradeDBCompatibility : DockerRemoteMachineBasedTest() {

    val accountsV1Contracts =
        NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.accounts",
            artifact = "accounts-contracts",
            version = "1.0"
        )

    val accountsV1Workflows =
        NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.accounts",
            artifact = "accounts-workflows",
            version = "1.0"
        )

    val accountsCurrentWorkflows =
        NodeBuilder.DeployedCordapp.fromClassPath("accounts-workflows")

    val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
        group = "com.r3.corda.lib.ci",
        artifact = "ci-workflows",
        version = "1.0"
    )
    val stressTesterCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")


    @Test
    fun `upgrade to current does not break H2`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.H2)
    }

    @Test
    fun `upgrade to current does not break postgres 9_6`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
    }

    @Test
    fun `upgrade to current does not break postgres 10_10`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
    }

    @Test
    fun `upgrade to current does not break postgres 11_5`() {
        runAccountsOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
    }

    private fun runAccountsOnNodeRunningDatabase(db: DeploymentMachineProvider.DatabaseType) {
        val randomString = generateRandomString()

        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)

        val deploymentResult = SingleNodeDeployment(
            NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                .withCordapp(stressTesterCordapp)
                .withCordapp(accountsV1Contracts)
                .withCordapp(accountsV1Workflows)
                .withCordapp(modernCiV1)
                .withDatabase(machineProvider.requestDatabase(db))
        ).withVersion(UnitOfDeployment.CORDA_4_5_1)
            .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachines.single()

        val issuedAccountBeforeUpgrade = nodeMachine.rpc {
            startFlow(
                ::CreateAccount,
                "testAccount"
            ).returnValue.getOrThrow()
        }

        nodeMachine.stopNode()
        nodeMachine.upgradeCordapp(accountsV1Workflows, accountsCurrentWorkflows)
        nodeMachine.startNode()

        val cordappVersionAfterUpgrade = nodeMachine.rpc {
            startFlow(
                ::CordappVersionDetector
            ).returnValue.getOrThrow()
        }

        MatcherAssert.assertThat(cordappVersionAfterUpgrade, `is`("2"))

        val retrievedAfterUpgrade = nodeMachine.rpc {
            startFlow(
                ::AccountInfoByUUID,
                issuedAccountBeforeUpgrade.state.data.identifier.id
            ).returnValue.getOrThrow()
        }
        MatcherAssert.assertThat(issuedAccountBeforeUpgrade.state.data, `is`(retrievedAfterUpgrade?.state?.data))
    }

}