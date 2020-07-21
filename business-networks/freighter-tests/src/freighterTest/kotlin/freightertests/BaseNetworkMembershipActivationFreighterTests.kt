package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployed
import freighter.deployments.SingleNodeDeployment
import freighter.installers.corda.OPEN_SOURCE
import freighter.machine.DeploymentMachineProvider
import freighter.testing.RemoteMachineBasedTest
import net.corda.bn.flows.ActivateMembershipFlow
import net.corda.bn.flows.CreateBusinessNetworkFlow
import net.corda.bn.flows.RequestMembershipFlow
import net.corda.bn.flows.SuspendMembershipFlow
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import utility.getOrThrow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

abstract class BaseNetworkMembershipActivationFreighterTests : RemoteMachineBasedTest() {

    private val bnoContracts = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-contracts")
    private val bnoWorkflows = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-workflows")
    private val bnoTesting = NodeBuilder.DeployedCordapp.fromClassPath("corda-business-network-testing-cordapp")
    private val freighterHelperCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")


    fun runNetworkMembershipActivationBenchmark(deploymentContext: DeploymentContext, numberOfParticpants: Int): BenchMarkResult {
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val networkId = UniqueIdentifier()
        val groupId = UniqueIdentifier()

        bnoNode.startNode()
        val memberShipStateNetworkId = createBusinessNetwork(bnoNode, networkId, groupId)

        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticpants, nodeGenerator)
        listOfGroupMembers.forEach {
            println("Starting Node " + it.name)
            it.startNode()
        }

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = listOfGroupMembers.associateBy(
                { it },
                {
                    it.rpc {
                        startFlow(::RequestMembershipFlow,
                                bnoNode.identity(),
                                memberShipStateNetworkId,
                                null,
                                null).returnValue.getOrThrow()
                    }.tx.outputStates.single() as MembershipState
                })

        for (member in nodeToMembershipIds) {
            bnoNode.rpc {
                startFlow(::ActivateMembershipFlow,
                        member.value.linearId,
                        null)
            }
        }


        val holderCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.ACTIVE))

        val membershipActiveTime =  measureTimeMillis{ waitForStatusUpdate(listOfGroupMembers, holderCriteria, bnoNode)}

        val subsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size/2).first()

        val membershipUpdateTime =  measureTimeMillis {
            for (node in subsetGroupMembers) {
                bnoNode.rpc {
                    startFlow(::SuspendMembershipFlow, nodeToMembershipIds[node]!!.linearId, null)
                }
            }
        }

        val suspendedStatusCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.SUSPENDED))
        val membershipSuspendTime =  measureTimeMillis{ waitForStatusUpdate(subsetGroupMembers, suspendedStatusCriteria, bnoNode)}



//        bnoNode.rpc {
//            startFlow(::CreateGroupFlow,
//                    memberShipStateNetworkId,
//                    newGroupId,
//                    newGroupName,
//                    subsetGroupMembers.map { it.linearId }.toSet(),
//                    null
//                    )
//        }

        bnoNode.nodeMachine.stopNode()
        listOfGroupMembers.forEach { it.stopNode() }

        return BenchMarkResult(membershipActiveTime,membershipUpdateTime, membershipSuspendTime)

    }

    private fun getMembershipStatusQueryCriteria(listOfMemberShipStatus:List<MembershipStatus>): QueryCriteria {
        return QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(listOfMemberShipStatus) }))
    }

    private fun waitForStatusUpdate(listOfGroupMembers: List<SingleNodeDeployed>, holderCriteria: QueryCriteria, bnoNode: SingleNodeDeployed) {
       val listToIterateOver =  listOfGroupMembers.toMutableList()
            while (listToIterateOver.isNotEmpty()) {
                val itr = listToIterateOver.iterator()
                while (itr.hasNext()) {
                    val currentNode = itr.next()
                    val actual = currentNode.rpc {
                        vaultQueryByCriteria(holderCriteria, MembershipState::class.java).states.map { it.state.data }
                    }
                    if (actual.isNotEmpty()) {
                        itr.remove()
                    }
                }
            }
    }

    private fun createBusinessNetwork(bnoNode: SingleNodeDeployed, networkId: UniqueIdentifier, groupId: UniqueIdentifier, groupName: String? = null): String {
        val output: SignedTransaction = bnoNode.rpc {
            startFlow(::CreateBusinessNetworkFlow,
                    networkId,
                    null,
                    groupId,
                    groupName,
                    null).returnValue.getOrThrow()
        }

        val memberShipStateNetworkId = (output.tx.outputStates.single() as MembershipState).networkId
        return memberShipStateNetworkId
    }

    private fun buildGroupMembershipNodes(numberOfParticpants: Int, nodeGenerator: () -> CompletableFuture<SingleNodeDeployed>): ArrayList<SingleNodeDeployed> {
        return arrayListOf<SingleNodeDeployed>().apply {
            (0 until numberOfParticpants).forEach {
                add(nodeGenerator().getOrThrow())
            }
        }
    }

    private fun createDeploymentGenerator(
            deploymentContext: DeploymentContext,
            indexGenerator: AtomicInteger = AtomicInteger(0)
    ): () -> CompletableFuture<SingleNodeDeployed> =
            run {
                ({ indexGen: AtomicInteger, deploymentContext: DeploymentContext, db: DeploymentMachineProvider.DatabaseType ->
                    {
                        SingleNodeDeployment(
                                NodeBuilder().withX500("O=Party${indexGen.getAndIncrement()}, C=IE, L=DUBLIN CN=Corda")
                                        .withCordapp(bnoContracts)
                                        .withCordapp(bnoWorkflows)
                                        .withCordapp(freighterHelperCordapp)
                                        .withCordapp(bnoTesting)
                        ).withVersion("4.4")
                                .withDistribution(OPEN_SOURCE)
                                .deploy(deploymentContext)
                    }
                })
            }(indexGenerator, deploymentContext, DeploymentMachineProvider.DatabaseType.PG_11_5)
}

data class BenchMarkResult(val membershipActiveTime:Long, val membershipUpdateTime: Long, val membershipSuspendTime: Long)

