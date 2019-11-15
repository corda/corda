package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.list
import net.corda.core.internal.readAllLines
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.InternalDriverDSL
import org.jboss.byteman.agent.submit.ScriptText
import org.jboss.byteman.agent.submit.Submit
import org.junit.Before

abstract class StatemachineErrorHandlingTest {

    val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    var counter = 0

    @Before
    fun setup() {
        counter = 0
    }

    internal fun startDriver(notarySpec: NotarySpec = NotarySpec(DUMMY_NOTARY_NAME), dsl: DriverDSL.() -> Unit) {
        driver(
            DriverParameters(
                notarySpecs = listOf(notarySpec),
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            dsl()
        }
    }

    internal fun DriverDSL.createBytemanNode(
        providedName: CordaX500Name,
        additionalCordapps: Collection<TestCordapp> = emptyList()
    ): NodeHandle {
        return (this as InternalDriverDSL).startNode(
            NodeParameters(
                providedName = providedName,
                rpcUsers = listOf(rpcUser),
                additionalCordapps = additionalCordapps
            ),
            bytemanPort = 12000
        ).getOrThrow()
    }

    internal fun DriverDSL.createNode(providedName: CordaX500Name, additionalCordapps: Collection<TestCordapp> = emptyList()): NodeHandle {
        return startNode(
            NodeParameters(
                providedName = providedName,
                rpcUsers = listOf(rpcUser),
                additionalCordapps = additionalCordapps
            )
        ).getOrThrow()
    }

    internal fun submitBytemanRules(rules: String) {
        val submit = Submit("localhost", 12000)
        submit.addScripts(listOf(ScriptText("Test script", rules)))
    }

    internal fun getBytemanOutput(nodeHandle: NodeHandle): List<String> {
        return nodeHandle.baseDirectory
            .list()
            .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
            .readAllLines()
    }

    @StartableByRPC
    @InitiatingFlow
    class SendAMessageFlow(private val party: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val session = initiateFlow(party)
            session.send("hello there")
            return "Finished executing test flow - ${this.runId}"
        }
    }

    @InitiatedBy(SendAMessageFlow::class)
    class SendAMessageResponder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
        }
    }

    @StartableByRPC
    class GetNumberOfCheckpointsFlow : FlowLogic<Long>() {
        override fun call(): Long {
            return serviceHub.jdbcSession().prepareStatement("select count(*) from node_checkpoints").use { ps ->
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
    }

    @StartableByRPC
    class GetHospitalCountersFlow : FlowLogic<HospitalCounts>() {
        override fun call(): HospitalCounts =
            HospitalCounts(
                serviceHub.cordaService(HospitalCounter::class.java).dischargeCounter,
                serviceHub.cordaService(HospitalCounter::class.java).observationCounter
            )
    }

    @CordaSerializable
    data class HospitalCounts(val discharge: Int, val observation: Int)

    @Suppress("UNUSED_PARAMETER")
    @CordaService
    class HospitalCounter(services: AppServiceHub) : SingletonSerializeAsToken() {
        var observationCounter: Int = 0
        var dischargeCounter: Int = 0

        init {
            StaffedFlowHospital.onFlowDischarged.add { _, _ ->
                ++dischargeCounter
            }
            StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
                ++observationCounter
            }
        }
    }
}