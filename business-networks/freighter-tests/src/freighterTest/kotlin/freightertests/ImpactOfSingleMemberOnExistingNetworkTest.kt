package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.testing.AzureTest
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Assertions
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
        runBenchmarkAgainstExpectedTime(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf20Nodes(){
        val participants = 20
        runBenchmarkAgainstExpectedTime(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf30Nodes(){
        val participants = 30
        runBenchmarkAgainstExpectedTime(participants,300000)
    }

    @Test
    fun testOfAddingASingleNodeTOAnExistingNetworkOf40Nodes(){
        val participants = 40
        runBenchmarkAgainstExpectedTime(participants,300000)
    }



    private fun runBenchmarkAgainstExpectedTime(participants: Int, maximumTime: Long) {
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = runBenchmark(deploymentContext, participants)


        benchmark.map {
            getLogger().info("${it.key} BenchMark ${it.value}")
        }

        benchmark.map {
            Assertions.assertTrue(it.value <= maximumTime)
        }
    }
}


abstract class AbstractImpactOfSingleMemberOnExistingNetworkTest:BaseBNFreighterTest(){

    fun runBenchmark(deploymentContext: DeploymentContext, numberOfParticipants: Int): Map<String, Long>{
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
        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers)

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