package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.AzureTest
import freighter.testing.DockerTest
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Test
import utility.getOrThrow
import kotlin.system.measureTimeMillis

@AzureTest
class ImpactOfSingleMemberOnExistingNetworkTest: AbstractImpactOfSingleMemberOnExistingNetworkTest() {

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(ImpactOfSingleMemberOnExistingNetworkTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf10Nodes(){
        val participants = 10
        runBenchmark(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf20Nodes(){
        val participants = 20
        runBenchmark(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf30Nodes(){
        val participants = 30
        runBenchmark(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf40Nodes(){
        val participants = 40
        runBenchmark(participants,300000)
    }

}

@DockerTest
class DockerImpactOfSingleMemberOnExistingNetworkTest : AbstractImpactOfSingleMemberOnExistingNetworkTest(){

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerNetworkMembershipActivationTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testScenario2Participants() {
        val numberOfParticipants = 2
        runBenchmark(numberOfParticipants, 300000)
    }

}


abstract class AbstractImpactOfSingleMemberOnExistingNetworkTest:BaseBNFreighterTest(){

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long>{
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        getLogger().info("Setting up Business Network")

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID,defaultGroupName)
        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)
        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        activateNetworkMembership(nodeToMembershipIds, bnoNode)
        val groupMembers = setupDefaultGroup(nodeToMembershipIds, bnoMembershipState, bnoNode, defaultGroupID, defaultGroupName)

        getLogger().info("Adding New Single Node")
        val newNode = nodeGenerator().getOrThrow()
        val newNodeToMembershipState = requestNetworkMembership(listOf(newNode), bnoNode,bnoMembershipState)

        val membershipActivation = measureTimeMillis {activateNetworkMembership(newNodeToMembershipState, bnoNode)}
        val groupAdditionTime = measureTimeMillis {
            groupMembers.add((newNodeToMembershipState[newNode] ?: error("Node Not Found")).linearId)
            addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers)
        }

        return mapOf("Membership Activation Time" to membershipActivation, "Group Addition Time" to groupAdditionTime)


    }
}