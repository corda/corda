package freightertests

import freighter.deployments.DeploymentContext
import freighter.deployments.SingleNodeDeployed
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.testing.AzureTest
import net.corda.bn.flows.AssignBNORoleFlow
import net.corda.bn.flows.AssignMemberRoleFlow
import net.corda.bn.flows.ModifyRolesFlow
import net.corda.bn.flows.RevokeMembershipFlow
import net.corda.bn.flows.SuspendMembershipFlow
import net.corda.bn.states.BNORole
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import utility.getOrThrow
import kotlin.system.measureTimeMillis

@AzureTest
class BnRolesFreighterTests: AbstractBnRolesFreighterTests() {

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    companion object {
        val logger: ExtendedLogger = LogManager.getContext().getLogger(BnRolesFreighterTests::class.java.name)
    }

    override fun getLogger(): ExtendedLogger {
        return logger
    }

    @Test
    fun testAllMembersAreBNOWith10Participants() {
        val numberOfParticipants = 10
        measureBenchmark(numberOfParticipants, 300000)
    }

    @Test
    fun testAllMembersAreBNOWith20Participants() {
        val numberOfParticipants = 20
        measureBenchmark(numberOfParticipants, 15000)
    }

    @Test
    fun testAllMembersAreBNOWith40Participants() {
        val numberOfParticipants = 40
        measureBenchmark(numberOfParticipants, 20000)
    }

    private fun measureBenchmark(numberOfParticipants: Int, maxTime:Long, doRevocation: Boolean = false) {
        logger.info("Running benchmark with $numberOfParticipants particpants")


        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = runBenchmark(deploymentContext, numberOfParticipants, doRevocation)

        benchmark.map {
            logger.info("${it.key} BenchMark ${it.value}")
        }

        benchmark.map {
            Assertions.assertTrue(it.value <= maxTime)
        }
    }
}

abstract class AbstractBnRolesFreighterTests :BaseBNFreighterTest(){

    fun runBenchmark(deploymentContext: DeploymentContext, numberOfParticipants: Int, doRevocation:Boolean): Map<String, Long>{
        val nodeGenerator = createDeploymentGenerator(deploymentContext)
        val bnoNode = nodeGenerator().getOrThrow()

        val newGroupId = UniqueIdentifier()
        val newGroupName = "InitialGroup"
        val networkId = UniqueIdentifier()
        val bnoMembershipState: MembershipState = createBusinessNetwork(bnoNode, networkId, newGroupId,newGroupName)

        //val groupStateForDefaultGroup = createGroup(bnoNode, bnoMembershipState, newGroupId, newGroupName)

        val listOfGroupMembers = buildGroupMembershipNodes(numberOfParticipants, nodeGenerator)

        val nodeToMembershipIds: Map<SingleNodeDeployed, MembershipState> = requestNetworkMembership(listOfGroupMembers, bnoNode, bnoMembershipState)
        activateNetworkMembership(nodeToMembershipIds, bnoNode)

        getLogger().info("Beginning to Assign BNO Roles")

        nodeToMembershipIds.values.forEach{
            bnoNode.rpc {
                getLogger().info("Assigning ${it.identity} bno role")
                startFlow(::ModifyRolesFlow, it.linearId, setOf(BNORole()),null).returnValue.getOrThrow()
            }
        }

        getLogger().info("Finished Assigning Roles")

        val membershipCriteria = getMembershipStatusQueryCriteria(listOf(MembershipStatus.ACTIVE))
        val membershipActiveTime = measureTimeMillis { waitForStatusUpdate(listOfGroupMembers, membershipCriteria) }
        val firstSubsetGroupMembers = nodeToMembershipIds.keys.chunked(nodeToMembershipIds.size / 2).first()

        getLogger().info("Beginning to Suspend")


        val membershipUpdateTime = measureTimeMillis {
            firstSubsetGroupMembers.map{
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

        if(doRevocation){
            val membershipRevocationTime = measureTimeMillis {
                for (node in firstSubsetGroupMembers) {
                    bnoNode.rpc {
                        startFlow(::RevokeMembershipFlow, nodeToMembershipIds[node]!!.linearId, null).returnValue.getOrThrow()
                    }
                }
            }

            val vaultUpdateTimeAfterDeletion = measureTimeMillis {
                checkGroupSizeIsAsExpectedInMembersVaults(firstSubsetGroupMembers, newGroupId,(nodeToMembershipIds.size-firstSubsetGroupMembers.size)+1)
            }
            benchmarkResults["Time Taken Revoke Membership of half"] = membershipRevocationTime
            benchmarkResults["Time Taken for Vault Update To Reflect Revocation in remaining members' Vaults"] = vaultUpdateTimeAfterDeletion
        }

        bnoNode.nodeMachine.stopNode()
        listOfGroupMembers.map { it.stopNode() }

        return benchmarkResults
    }
}