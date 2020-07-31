package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.AzureTest
import freighter.testing.DockerTest
import net.corda.bn.flows.SuspendMembershipFlow
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@AzureTest
class ImpactsOfDifferingGroupSizesOnTheSameNetwork: AbstractImpactsOfDifferingGroupSizesOnTheSameNetwork() {

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(ImpactsOfDifferingGroupSizesOnTheSameNetwork::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testScenario15Participants() {
        val numberOfParticipants = 15
        runBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testScenario30Participants() {
        val numberOfParticipants = 30
        runBenchmark(numberOfParticipants, 300000)
    }
}

@DockerTest
class DockerImpactsOfDifferingGroupSizesOnTheSameNetwork : AbstractImpactsOfDifferingGroupSizesOnTheSameNetwork(){

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerImpactsOfDifferingGroupSizesOnTheSameNetwork::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testScenario3Participants() {
        val numberOfParticipants = 3
        runBenchmark(numberOfParticipants, 300000)
    }

}


abstract class AbstractImpactsOfDifferingGroupSizesOnTheSameNetwork:BaseBNFreighterTest() {

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID,defaultGroupName)
        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        activateNetworkMembership(nodeToMembershipIds, bnoNode)

        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers)

        val subsetGroupMembers = nodeToMembershipIds.values.chunked(nodeToMembershipIds.size / 3) as MutableList
        val smallGroup = subsetGroupMembers.first()
        subsetGroupMembers.removeAt(0)
        val largeGroup =  subsetGroupMembers.flatten()

        getLogger().info("Small Group Size Set to ${smallGroup.size}")
        getLogger().info("Large Group Size Set to ${largeGroup.size}")

        val groupIndex = AtomicInteger(0)
        createSubGroup(smallGroup, groupIndex, bnoMembershipState, bnoNode)
        createSubGroup(largeGroup, groupIndex, bnoMembershipState, bnoNode)
        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))

        val smallGroupNodeToBeSuspended = nodeToMembershipIds.filterValues { it == smallGroup.first() }.keys
        val largeGroupNodeToBeSuspended = nodeToMembershipIds.filterValues { it == largeGroup.first() }.keys

        val smallGroupSuspendTimeFlow = measureTimeMillis {
            bnoNode.rpc {
                startFlow(::SuspendMembershipFlow, smallGroup.first().linearId, null)
            }
        }
        val smallGroupRegistryTime = measureTimeMillis { waitForStatusUpdate(smallGroupNodeToBeSuspended.toList(), suspendedStatusCriteria) }

        val bigGroupSuspendTimeFlow = measureTimeMillis {
            bnoNode.rpc {
                startFlow(::SuspendMembershipFlow, largeGroup.first().linearId, null)
            }
        }
        val bigGroupRegistryTime = measureTimeMillis { waitForStatusUpdate(largeGroupNodeToBeSuspended.toList(), suspendedStatusCriteria) }

        return mapOf("Time taken for Small Group to Run Suspend Flow" to smallGroupSuspendTimeFlow,
                "Time taken for Small Group Suspension to Register in Vault" to smallGroupRegistryTime,
                "Time take for Large Group to Run Suspend Flow" to bigGroupSuspendTimeFlow,
                "Time taken for Large Group Suspension to Register in Vault" to bigGroupRegistryTime
                )
    }
}