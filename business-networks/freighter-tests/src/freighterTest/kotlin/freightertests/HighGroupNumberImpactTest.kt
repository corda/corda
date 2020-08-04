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

    private companion object {
        const val numberOfParticipants = 10
        const val cutOffTime: Long = 300000
    }

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(AddingASingleMemberToNetworkAndGroupTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testScenario() {
        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

@DockerTest
class DockerHighGroupNumberImpactTest : AbstractHighGroupNumberImpactTest() {

    private companion object {
        const val numberOfParticipants = 2
        const val cutOffTime: Long = 300000
    }

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerNetworkMembershipActivationTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testScenario() {
        runBenchmark(numberOfParticipants, cutOffTime)
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

        val groupIndex = AtomicInteger(0)

        listOfGroupMembers.map {
            val smallGroup = listOf((nodeToMembershipIds[it] ?: error("Could Not Find given Node")))
            createSubGroup(smallGroup, groupIndex, bnoMembershipState, bnoNode)
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
                "Time taken to register in Vault" to vaultRegistryTime)
    }
}