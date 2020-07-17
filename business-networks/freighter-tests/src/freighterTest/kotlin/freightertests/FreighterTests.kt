package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployed
import freighter.deployments.SingleNodeDeployment
import freighter.installers.corda.OPEN_SOURCE
import freighter.machine.DeploymentMachineProvider
import freighter.testing.DockerRemoteMachineBasedTest
import net.corda.bn.flows.CreateBusinessNetworkFlow
import net.corda.bn.testing.DummyIdentity
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class FreighterTests : DockerRemoteMachineBasedTest() {

    private val bnoContracts = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-contracts")
    private val bnoWorkflows = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-workflows")
    private val bnoTesting = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-testing-cordapp")
    private val freighterHelperCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")

    @Test
    fun test() {
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)

        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkId = UniqueIdentifier()
        val groupId = UniqueIdentifier()
        val businessId = DummyIdentity("ImARealBNOY")
        val groupName = "GroupOfBnoyos"

//        val listOfGroupMembers = listOf(nodeGenerator(), nodeGenerator(), nodeGenerator()).map { it.getOrThrow() }

        val output = bnoNode.rpc {
            startFlow(::CreateBusinessNetworkFlow,
                    networkId,
                    businessId,
                    groupId,
                    groupName,
                    null).returnValue.getOrThrow()
        }

        //Need to get a value from the state returned from teh CreateBusinessNetworkFlow

//        for (node in listOfGroupMembers) {
//            node.rpc {
//                startFlow(::RequestMembershipFlow,
//                        bnoNode.identity(),
//                        networkId.toString(),
//                        businessId,
//                        null).returnValue.getOrThrow()
//            }
//        }
    }

//    private fun createNodeDeployment(randomString: String, deploymentContext: DeploymentContext): SingleNodeDeployed {
//        val deploymentResult = SingleNodeDeployment(
//                NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
//                        .withCordapp(bnoContracts)
//                        .withCordapp(bnoWorkflows)
//                        .withDatabase(machineProvider.requestDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5))
//        ).withVersion("4.5")
//                .withDistribution(OPEN_SOURCE)
//                .deploy(deploymentContext)
//
//        return deploymentResult.getOrThrow()
//    }

    private fun createDeploymentGenerator(
            deploymentContext: DeploymentContext,
            indexGenerator: AtomicInteger = AtomicInteger(0)
    ): () -> CompletableFuture<SingleNodeDeployed> =
            run {
                ({ indexGen: AtomicInteger, deploymentContext: DeploymentContext, db: DeploymentMachineProvider.DatabaseType ->
                    {
                        SingleNodeDeployment(
                                NodeBuilder().withX500("O=Party${indexGen.getAndIncrement()}, C=IE, L=DUBLIN CN=Corda")
                                        .withCordapp(bnoContracts)
                                        .withCordapp(bnoWorkflows)
                                        .withCordapp(freighterHelperCordapp)
                                        .withCordapp(bnoTesting)
                        ).withVersion("4.4")
                                .withDistribution(OPEN_SOURCE)
                                .deploy(deploymentContext)
                    }
                })
            }(indexGenerator, deploymentContext, DeploymentMachineProvider.DatabaseType.PG_11_5)
}
