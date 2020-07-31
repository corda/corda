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
class HighGroupNumberImpactTest : AbstractHighGroupNumberImpactTest() {

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(AddingASingleMemberToNetworkAndGroupTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testWith10Participants() {
        val numberOfParticipants = 10
        runBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testScenario20Participants() {
        val numberOfParticipants = 20
        runBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testScenario30Participants() {
        val numberOfParticipants = 30
        runBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testScenario40Participants() {
        val numberOfParticipants = 40
        runBenchmark(numberOfParticipants, 300000)
    }
}

@DockerTest
class DockerHighGroupNumberImpactTest : AbstractHighGroupNumberImpactTest(){

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerNetworkMembershipActivationTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testScenario10Participants() {
        val numberOfParticipants = 10
        runBenchmark(numberOfParticipants, 300000)
    }

}

abstract class AbstractHighGroupNumberImpactTest : BaseBNFreighterTest() {

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID, defaultGroupName)
        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        activateNetworkMembership(nodeToMembershipIds, bnoNode)
        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers)

        val groupIndex = AtomicInteger(0)

        listOfGroupMembers.map {
            val subsetGroupName = "subGroup-${groupIndex.incrementAndGet()}"
            val subGroupId = UniqueIdentifier()
            val smallGroup = mutableListOf(bnoMembershipState.linearId, (nodeToMembershipIds[it] ?: error("")).linearId)
            createGroup(bnoNode, bnoMembershipState, subGroupId, subsetGroupName)
            addMembersToAGroup(bnoNode, subGroupId, subsetGroupName, smallGroup)
        }

        val nodeToSuspend = listOfGroupMembers.last()

        val suspensionTime = measureTimeMillis {
            bnoNode.rpc {
                startFlow(::SuspendMembershipFlow, nodeToMembershipIds[nodeToSuspend]!!.linearId, null)
            }
        }

        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))
        val vaultRegistryTime = measureTimeMillis { waitForStatusUpdate(listOf(nodeToSuspend), suspendedStatusCriteria) }

        return mapOf("Time Taken To Suspend A Single Node" to suspensionTime,
                "Time taken to register in Vault" to vaultRegistryTime )
    }
}