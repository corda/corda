package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.AzureTest
import freighter.testing.DockerTest
import net.corda.bn.flows.ActivateMembershipFlow
import net.corda.bn.flows.ModifyGroupFlow
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Test
import utility.getOrThrow
import kotlin.system.measureTimeMillis

@AzureTest
class AddingASingleMemberToNetworkAndGroupTest : AbstractAddingASingleMemberToNetworkAndGroupTest() {

    private companion object {
        const val numberOfParticipants = 10
        const val cutOffTime: Long = 300000
    }

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(AddingASingleMemberToNetworkAndGroupTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testMembershipSlowActivationWith10Participants() {
        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

@DockerTest
class DockerAddingASingleMemberToNetworkAndGroupTest : AbstractAddingASingleMemberToNetworkAndGroupTest() {

    private val numberOfParticipants = 2
    private val cutOffTime: Long = 300000

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(DockerAddingASingleMemberToNetworkAndGroupTest::class.java.name)
    }

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun testMembershipSlowActivationWith2Participants() {
        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

abstract class AbstractAddingASingleMemberToNetworkAndGroupTest : BaseBNFreighterTest() {
    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkID = UniqueIdentifier()
        val defaultGroupID = UniqueIdentifier()
        val defaultGroupName = "InitialGroup"

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkID, defaultGroupID, defaultGroupName)
        val listOfNetworkMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)
        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfNetworkMembers, bnoNode, bnoMembershipState)

        val listOfPrivateGroupMember = mutableListOf(bnoMembershipState.linearId)

        val slowGroupAddBenchmark = measureTimeMillis {
            nodeToMembershipIds.values.map {
                bnoNode.rpc {
                    startFlow(::ActivateMembershipFlow,
                            it.linearId,
                            null).returnValue.getOrThrow()
                }
                listOfPrivateGroupMember.add(it.linearId)
                bnoNode.rpc {
                    startFlow(::ModifyGroupFlow,
                            defaultGroupID,
                            defaultGroupName,
                            listOfPrivateGroupMember.toMutableSet(),
                            null).returnValue.getOrThrow()
                }
            }
        }

        val vaultUpdateTime = measureTimeMillis {
            checkGroupSizeIsAsExpectedInMembersVaults(listOfNetworkMembers, defaultGroupID, listOfPrivateGroupMember.size)
        }

        val benchMarkResultMap = mutableMapOf(
                "Members Added Individually to group" to slowGroupAddBenchmark,
                "Updates to Vault" to vaultUpdateTime)

        bnoNode.nodeMachine.stopNode()
        listOfNetworkMembers.map { it.stopNode() }

        return benchMarkResultMap
    }
}