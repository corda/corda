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
import kotlin.system.measureTimeMillis

@AzureTest
class AzureNetworkMembershipActivationTest : AbstractNetworkMembershipActivationTest() {

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    companion object {
        val logger: ExtendedLogger = LogManager.getContext().getLogger(AzureNetworkMembershipActivationTest::class.java.name)
    }

    override fun getLogger(): ExtendedLogger {
        return logger
    }

    @Test
    fun testMembershipActivationWithParticipants() {
        val numberOfParticipants = 5
        runBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith10Participants() {
        val numberOfParticipants = 10
        runBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith15Participants() {
        val numberOfParticipants = 15
        runBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith20Participants() {
        val numberOfParticipants = 20
        runBenchmark(numberOfParticipants, 20000)
    }

    @Test
    fun testMembershipActivationWith40Participants() {
        val numberOfParticipants = 40
        runBenchmark(numberOfParticipants, 30000)
    }

}

@DockerTest
class DockerNetworkMembershipActivationTest : AbstractNetworkMembershipActivationTest(){

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerNetworkMembershipActivationTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testActivation2Participants() {
        val numberOfParticipants = 2
        runBenchmark(numberOfParticipants, 300000)
    }

}

abstract class AbstractNetworkMembershipActivationTest : BaseBNFreighterTest() {

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID,defaultGroupName)
        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        val membershipActiveTime = measureTimeMillis {
            activateNetworkMembership(nodeToMembershipIds, bnoNode)
        }
        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers)

        val membershipCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.ACTIVE))
        val membershipInVault = measureTimeMillis { waitForStatusUpdate(listOfGroupMembers, membershipCriteria) }
        val subsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size / 2).first()


        measureTimeMillis {
            for (node in subsetGroupMembers) {
                bnoNode.rpc {
                    startFlow(::SuspendMembershipFlow, nodeToMembershipIds[node]!!.linearId, null)
                }
            }
        }

        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))
        val membershipSuspendTime = measureTimeMillis { waitForStatusUpdate(subsetGroupMembers, suspendedStatusCriteria) }

        bnoNode.nodeMachine.stopNode()
        listOfGroupMembers.forEach { it.stopNode() }

        return mapOf("Membership Activation" to membershipActiveTime,"Membership in Members Vault" to membershipInVault, "Membership Update Time" to membershipActiveTime, "Membership Suspend Time" to membershipSuspendTime)
    }
}