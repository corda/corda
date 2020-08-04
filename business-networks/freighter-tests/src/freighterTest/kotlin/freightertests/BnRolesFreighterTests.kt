package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.AzureTest
import freighter.testing.DockerTest
import net.corda.bn.flows.AssignBNORoleFlow
import net.corda.bn.flows.RevokeMembershipFlow
import net.corda.bn.flows.SuspendMembershipFlow
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

    private companion object {
        const val numberOfParticipants = 10
        const val cutOffTime: Long = 300000
    }

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    override fun getLogger(): ExtendedLogger {
        return LogManager.getContext().getLogger(BnRolesFreighterTests::class.java.name)
    }

    @Test
    fun runScenario() {
        runBenchmark(numberOfParticipants, cutOffTime)
    }
}

@DockerTest
class DockerBnRolesFreighterTests : AbstractBnRolesFreighterTests() {

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

        val timeTakenToAssignBnoRole = measureTimeMillis {
            nodeToMembershipIds.map { nodeTOMembershipId ->
                addMembersToAGroup(bnoNode, newGroupId, newGroupName, groupMembers)
                bnoNode.rpc {
                    getLogger().info("Assigning ${nodeTOMembershipId.value.identity} bno role")
                    startFlow(::AssignBNORoleFlow, nodeTOMembershipId.value.linearId, null).returnValue.getOrThrow()
                }
            }
        }

        getLogger().info("Finished Assigning Roles")

        val firstSubsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size / 2).first()

        getLogger().info("Beginning to Suspend")

        val membershipUpdateTime = measureTimeMillis {
            firstSubsetGroupMembers.map {
                bnoNode.rpc {
                    getLogger().info("${it.nodeMachine.identity()} membership will be suspended.")
                    startFlow(::SuspendMembershipFlow, (nodeToMembershipIds[it]
                            ?: error("Could Not Find MembershipState for Node")).linearId, null).returnValue.getOrThrow()
                }
            }
        }

        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))
        val membershipSuspendTime = measureTimeMillis { waitForStatusUpdate(firstSubsetGroupMembers, suspendedStatusCriteria) }

        val benchmarkResults = mutableMapOf(
                "Time Taken To Assign BNO Roles" to timeTakenToAssignBnoRole,
                "Membership Update Time" to membershipUpdateTime,
                "Time Taken To Register Suspension in All Vaults" to membershipSuspendTime)

        val membershipRevocationTime = measureTimeMillis {
            for (node in firstSubsetGroupMembers) {
                bnoNode.rpc {
                    startFlow(::RevokeMembershipFlow, (nodeToMembershipIds[node]
                            ?: error("Could Not Find MembershipState for Node")).linearId, null).returnValue.getOrThrow()
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
}


