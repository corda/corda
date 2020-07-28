package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.testing.AzureTest
import net.corda.bn.flows.ActivateMembershipFlow
import net.corda.bn.flows.ModifyGroupFlow
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import utility.getOrThrow
import kotlin.system.measureTimeMillis

@AzureTest
class BnRolesFrieghterTests: AbstractBnRolesFrieghterTests() {

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    @Test
    fun testMembershipSlowActivationWith20Participants() {
        val numberOfParticipants = 20

        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = addingNodesToAGroupOneAtATimeBenchmark(deploymentContext, numberOfParticipants)

        benchmark.map {
            BaseBNFreighterTest.logger.info("${it.key} BenchMark ${it.value}")
        }

        benchmark.map {
            Assertions.assertTrue(it.value <= 300000)
        }
    }
}

abstract class AbstractBnRolesFrieghterTests() :BaseBNFreighterTest(){

    fun addingNodesToAGroupOneAtATimeBenchmark(deploymentContext: DeploymentContext, numberOfParticpants: Int): Map<String, Long>{
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, UniqueIdentifier(), UniqueIdentifier())

        val newGroupId = UniqueIdentifier()
        val newGroupName = "InitialGroup"
        val groupStateForDefaultGroup = createGroup(bnoNode, bnoMembershipState, newGroupId, newGroupName)

        val listOfNetworkMembers = buildGroupMembershipNodes(numberOfParticpants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requrestNetworkMembership(listOfNetworkMembers, bnoNode, bnoMembershipState)

        val listOfPrivateGroupMember = mutableListOf(bnoMembershipState.linearId)

        val slowGroupAddBenchmark = measureTimeMillis{
            nodeToMembershipIds.map {
                bnoNode.rpc {
                    startFlow(::ActivateMembershipFlow,
                            it.value.linearId,
                            null)
                }
                listOfPrivateGroupMember.add(it.value.linearId)
                bnoNode.rpc {
                    startFlow(::ModifyGroupFlow,
                            newGroupId,
                            newGroupName,
                            listOfPrivateGroupMember.toMutableSet(),
                            null)
                }
            }
        }

        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(groupStateForDefaultGroup.linearId)))

        val vaultUpdateTime = measureTimeMillis {
            checkGroupSizeIsAsExpectedInMembersVaults(listOfNetworkMembers, criteria, listOfPrivateGroupMember.size)
        }

        listOfPrivateGroupMember.remove(listOfPrivateGroupMember.last())

        bnoNode.rpc {
            startFlow(::ModifyGroupFlow,
                    newGroupId,
                    newGroupName,
                    listOfPrivateGroupMember.toMutableSet(),
                    null)
        }

        val vaultUpdateTimeAfterDeletion = measureTimeMillis {
            checkGroupSizeIsAsExpectedInMembersVaults(listOfNetworkMembers, criteria, listOfPrivateGroupMember.size)
        }

        bnoNode.nodeMachine.stopNode()
        listOfNetworkMembers.forEach { it.stopNode() }

        return mapOf("Members Added Individually to group" to slowGroupAddBenchmark,
                "Updates to Vault" to vaultUpdateTime,
                "Update to Members Vault after Single Member is removed" to vaultUpdateTimeAfterDeletion)

    }
}