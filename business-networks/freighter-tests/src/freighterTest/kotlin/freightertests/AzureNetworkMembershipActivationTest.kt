package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.testing.AzureTest
import net.corda.bn.flows.SuspendMembershipFlow
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Assertions
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
    fun testMembershipActivationWithSplitGroupParticipants() {
        val numberOfParticipants = 5
        runBaseActivationBenchmark(numberOfParticipants, 15000, true)
    }

    @Test
    fun testMembershipActivationWithParticipants() {
        val numberOfParticipants = 5
        runBaseActivationBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith10Participants() {
        val numberOfParticipants = 10
        runBaseActivationBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith15Participants() {
        val numberOfParticipants = 15
        runBaseActivationBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testMembershipActivationWith20Participants() {
        val numberOfParticipants = 20
        runBaseActivationBenchmark(numberOfParticipants, 20000)
    }

    @Test
    fun testMembershipActivationWith40Participants() {
        val numberOfParticipants = 40
        runBaseActivationBenchmark(numberOfParticipants, 30000)
    }

    private fun runBaseActivationBenchmark(numberOfParticipants: Int, cutOffTime: Long, groupSplitMode: Boolean = false) {
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = runNetworkMembershipActivationBenchmark(deploymentContext, numberOfParticipants)

        benchmark.map {
            logger.info("${it.key} BenchMark ${it.value}")
        }

        benchmark.map {
            Assertions.assertTrue(it.value <= cutOffTime)
        }
    }
}

abstract class AbstractNetworkMembershipActivationTest : BaseBNFreighterTest() {

    fun runNetworkMembershipActivationBenchmark(deploymentContext: DeploymentContext, numberOfParticipants: Int, doGroupSplit: Boolean = false): Map<String, Long> {
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

        if (doGroupSplit) {
            val subsetGroupName = "subGroup"
            val subGroupId = UniqueIdentifier()

            val subGroupMembers = subsetGroupMembers.map { nodeToMembershipIds.get(it)!!.linearId } as MutableList
            groupMembers.add(0, bnoMembershipState.linearId)

            createGroup(bnoNode, bnoMembershipState, subGroupId, subsetGroupName)
            addMembersToAGroup(bnoNode, subGroupId, subsetGroupName, subGroupMembers)
        }

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