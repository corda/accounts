package freighter.testing

import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByName
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.deployments.UnitOfDeployment
import freighter.deployments.UnitOfDeployment.Companion.CORDA_4_6_SNAPSHOT
import freighter.installers.corda.ENTERPRISE
import freighter.installers.corda.OPEN_SOURCE
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert
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
        ).withVersion(UnitOfDeployment.CORDA_4_6_SNAPSHOT)
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
        ).withVersion(UnitOfDeployment.CORDA_4_6_SNAPSHOT)
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

    @Test
    fun `accounts can be loaded on a 4_5 node running H2 on ENT and upgraded to 4_6`() {
        val randomString = generateRandomString()
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val deploymentResult = SingleNodeDeployment(
            NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                .withCordapp(stressTesterCordapp)
                .withCordapp(accountsContracts)
                .withCordapp(accountsWorkflows)
                .withCordapp(modernCiV1)
                .withDatabase(machineProvider.requestDatabase(DeploymentMachineProvider.DatabaseType.H2))
        ).withVersion(UnitOfDeployment.CORDA_4_5_1)
            .withDistribution(ENTERPRISE)
            .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachine
        val account45 = nodeMachine.rpc {
            startFlow(
                ::CreateAccount,
                "testAccount"
            ).returnValue.getOrThrow()
        }

        nodeMachine.upgradeCorda(CORDA_4_6_SNAPSHOT)
        val upgradedVersionString = nodeMachine.rpc {
            nodeDiagnosticInfo().version
        }
        MatcherAssert.assertThat(upgradedVersionString, `is`("4.6-SNAPSHOT"))
        val retrievedAccount = nodeMachine.rpc {
            startFlow(
                ::AccountInfoByName,
                "testAccount"
            ).returnValue.getOrThrow()
        }.single()


        MatcherAssert.assertThat(account45, `is`(retrievedAccount))


    }
}