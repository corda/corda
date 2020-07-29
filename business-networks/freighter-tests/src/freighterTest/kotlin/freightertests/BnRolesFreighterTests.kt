package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.AzureTest
import freighter.testing.DockerTest
import net.corda.bn.flows.AssignMemberRoleFlow
import net.corda.bn.flows.ModifyRolesFlow
import net.corda.bn.flows.RevokeMembershipFlow
import net.corda.bn.flows.SuspendMembershipFlow
import net.corda.bn.states.BNORole
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Test
import utility.getOrThrow
import kotlin.system.measureTimeMillis

@AzureTest
class BnRolesFreighterTests : AbstractBnRolesFreighterTests() {

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(BnRolesFreighterTests::class.java.name)
    }

    @Test
    fun testAllMembersAreBNOWith10Participants() {
        val numberOfParticipants = 10
        runBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testAllMembersAreBNOWith20Participants() {
        val numberOfParticipants = 20
        runBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testAllMembersAreBNOWith40Participants() {
        val numberOfParticipants = 40
        runBenchmark(numberOfParticipants, 20000)
    }
}

@DockerTest
class DockerBnRolesFreighterTests : AbstractBnRolesFreighterTests() {

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

abstract class AbstractBnRolesFreighterTests : BaseBNFreighterTest() {

    override fun runScenario(numberOfParticipants: Int, deploymentContext: DeploymentContext): Map<String, Long> {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val newGroupId = UniqueIdentifier()
        val newGroupName = "InitialGroup"
        val networkId = UniqueIdentifier()
        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkId, newGroupId, newGroupName)

        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        activateNetworkMembership(nodeToMembershipIds, bnoNode)

        val groupMembers = nodeToMembershipIds.values.map { it.linearId } as MutableList
        groupMembers.add(0, bnoMembershipState.linearId)
        addMembersToAGroup(bnoNode, newGroupId, newGroupName, groupMembers)
        checkGroupSizeIsAsExpectedInMembersVaults(listOfGroupMembers, newGroupId, groupMembers.size)

        getLogger().info("Beginning to Assign BNO Roles")

        nodeToMembershipIds.forEach { nodeTOMembershipId ->
            bnoNode.rpc {
                getLogger().info("Assigning ${nodeTOMembershipId.value.identity} bno role")
                startFlow(::ModifyRolesFlow, nodeTOMembershipId.value.linearId, setOf(BNORole()), null).returnValue.getOrThrow()
            }
            while (!checkBnoRoleInVault(nodeTOMembershipId)) {
                getLogger().info("Waiting for BNO Role To Propagate To Vault")
            }
        }

        getLogger().info("Finished Assigning Roles")

        val membershipCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.ACTIVE))
        val membershipActiveTime = measureTimeMillis { waitForStatusUpdate(listOfGroupMembers, membershipCriteria) }
        val firstSubsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size / 2).first()

        getLogger().info("Beginning to Suspend")

        val membershipUpdateTime = measureTimeMillis {
            firstSubsetGroupMembers.map {
                bnoNode.rpc {
                    getLogger().info("${it.nodeMachine.identity()} membership will be suspended.")
                    startFlow(::AssignMemberRoleFlow, nodeToMembershipIds[it]!!.linearId, null).returnValue.getOrThrow()
                    startFlow(::SuspendMembershipFlow, nodeToMembershipIds[it]!!.linearId, null).returnValue.getOrThrow()
                }
            }
        }

        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))
        val membershipSuspendTime = measureTimeMillis { waitForStatusUpdate(firstSubsetGroupMembers, suspendedStatusCriteria) }

        val benchmarkResults = mutableMapOf("Membership Activation" to membershipActiveTime,
                "Membership Update Time" to membershipUpdateTime,
                "Time Taken To Register Suspension in All Vaults" to membershipSuspendTime)

        val membershipRevocationTime = measureTimeMillis {
            for (node in firstSubsetGroupMembers) {
                bnoNode.rpc {
                    startFlow(::RevokeMembershipFlow, nodeToMembershipIds[node]!!.linearId, null).returnValue.getOrThrow()
                }
            }
        }

        val vaultUpdateTimeAfterDeletion = measureTimeMillis {
            checkGroupSizeIsAsExpectedInMembersVaults(firstSubsetGroupMembers, newGroupId, (nodeToMembershipIds.size - firstSubsetGroupMembers.size) + 1)
        }
        benchmarkResults["Time Taken Revoke Membership of half"] = membershipRevocationTime
        benchmarkResults["Time Taken for Vault Update To Reflect Revocation in remaining members' Vaults"] = vaultUpdateTimeAfterDeletion


        bnoNode.nodeMachine.stopNode()
        listOfGroupMembers.map { it.stopNode() }

        return benchmarkResults
    }

    private fun checkBnoRoleInVault(nodeTOMembershipId: Map.Entry<SingleNodeDeployed, MembershipState>): Boolean {
        return nodeTOMembershipId.key.rpc {
            vaultQuery(MembershipState::class.java).states.map { it.state.data }
        }.first().roles.contains(BNORole())
    }
}


