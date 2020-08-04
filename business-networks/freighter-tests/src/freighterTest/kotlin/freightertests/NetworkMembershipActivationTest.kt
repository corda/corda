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

    private companion object {
        const val numberOfParticipants = 20
        const val cutOffTime: Long = 300000
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(AzureNetworkMembershipActivationTest::class.java.name)
    }

    @Test
    fun testMembershipActivationWithParticipants() {

        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

@DockerTest
class DockerNetworkMembershipActivationTest : AbstractNetworkMembershipActivationTest() {

    private companion object {
        const val numberOfParticipants = 2
        const val cutOffTime: Long = 300000
    }

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerNetworkMembershipActivationTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testActivation() {
        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

abstract class AbstractNetworkMembershipActivationTest : BaseBNFreighterTest() {

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID, defaultGroupName)
        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        val membershipActiveTime = measureTimeMillis {
            activateNetworkMembership(nodeToMembershipIds, bnoNode)
        }
        val membershipCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.ACTIVE))
        val membershipInVault = measureTimeMillis { waitForStatusUpdate(listOfGroupMembers, membershipCriteria) }

        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        val timeItTookToOnboardToGroup = measureTimeMillis { addMembersToAGroup(bnoNode, defaultGroupID, defaultGroupName, groupMembers) }

        val subsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size / 2).first()

        val timeTakenToRunSuspendFlow = measureTimeMillis {
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

        return mapOf("Membership Activation" to membershipActiveTime,
                "Added To Group Flow" to timeItTookToOnboardToGroup,
                "Membership in Members Vault" to membershipInVault,
                "Time Take to Run Suspend Flow" to timeTakenToRunSuspendFlow,
                "Time Take For Suspend Flow to Appear in Vault" to membershipSuspendTime)
    }
}