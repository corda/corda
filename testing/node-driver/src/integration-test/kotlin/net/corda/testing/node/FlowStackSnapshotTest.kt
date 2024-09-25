package net.corda.testing.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.read
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.div
import kotlin.io.path.useDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@CordaSerializable
data class StackSnapshotFrame(val method: String, val clazz: String, val dataTypes: List<String?>, val flowId: String? = null)

/**
 * Calculates the count of full and empty frames. We consider frame to be empty if there is no stack data
 * associated with it (i.e. the stackObjects is an empty list). Otherwise (i.e. when the stackObjects is not
 * an empty list the frame is considered to be full.
 */
fun convertToStackSnapshotFrames(snapshot: FlowStackSnapshot): List<StackSnapshotFrame> {
    return snapshot.stackFrames.map { frame ->
        val dataTypes = frame.stackObjects.map {
            if (it == null) null else it::class.qualifiedName
        }
        val stackTraceElement = frame.stackTraceElement
        StackSnapshotFrame(stackTraceElement.methodName, stackTraceElement.className, dataTypes)
    }
}

/**
 * Flow that during its execution performs calls with side effects in terms of Quasar. The presence of
 * side effect calls drives Quasar decision on stack optimisation application. The stack optimisation method aims
 * to reduce the amount of data stored on Quasar stack to minimum and is based on static code analyses performed during
 * the code instrumentation phase, during which Quasar checks if a method performs side effect calls. If not,
 * the method is annotated to be optimised, meaning that none of its local variables are stored on the stack and
 * during the runtime the method can be replayed with a guarantee to be idempotent.
 */
@StartableByRPC
class SideEffectFlow : FlowLogic<List<StackSnapshotFrame>>() {
    private var sideEffectField = ""

    @Suspendable
    override fun call(): List<StackSnapshotFrame> {
        sideEffectField = "sideEffectInCall"
        // Expected to be on stack
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_CALL_VALUE
        return retrieveStackSnapshot()
    }

    @Suspendable
    fun retrieveStackSnapshot(): List<StackSnapshotFrame> {
        sideEffectField = "sideEffectInRetrieveStackSnapshot"
        // Expected to be on stack
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_RETRIEVE_STACK_SNAPSHOT_VALUE
        val snapshot = flowStackSnapshot()
        return convertToStackSnapshotFrames(snapshot!!)
    }

}

/**
 * Flow that during its execution performs calls with no side effects in terms of Quasar.
 * Thus empty frames are expected on in the stack snapshot as Quasar will optimise.
 */
@StartableByRPC
class NoSideEffectFlow : FlowLogic<List<StackSnapshotFrame>>() {

    @Suspendable
    override fun call(): List<StackSnapshotFrame> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = "inCall"
        return retrieveStackSnapshot()
    }

    @Suspendable
    fun retrieveStackSnapshot(): List<StackSnapshotFrame> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = "inRetrieveStackSnapshot"
        val snapshot = flowStackSnapshot()
        return convertToStackSnapshotFrames(snapshot!!)
    }
}

object Constants {
    const val IN_PERSIST_VALUE = "inPersist"
    const val IN_CALL_VALUE = "inCall"
    const val IN_RETRIEVE_STACK_SNAPSHOT_VALUE = "inRetrieveStackSnapshot"
    const val USER = "User"
    const val PASSWORD = "Password"

}

/**
 * No side effect flow that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingNoSideEffectFlow : FlowLogic<StateMachineRunId>() {

    @Suspendable
    override fun call(): StateMachineRunId {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = "inCall"
        persist()
        return stateMachine.id
    }

    @Suspendable
    fun persist() {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = "inPersist"
        persistFlowStackSnapshot()
    }
}

/**
 * Flow with side effects that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingSideEffectFlow : FlowLogic<StateMachineRunId>() {

    @Suspendable
    override fun call(): StateMachineRunId {
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_CALL_VALUE
        persist()
        return stateMachine.id
    }

    @Suspendable
    fun persist() {
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_PERSIST_VALUE
        persistFlowStackSnapshot()
    }
}

/**
 * Similar to [PersistingSideEffectFlow] but aims to produce multiple snapshot files.
 */
@StartableByRPC
class MultiplePersistingSideEffectFlow(private val persistCallCount: Int) : FlowLogic<StateMachineRunId>() {

    @Suspendable
    override fun call(): StateMachineRunId {
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_CALL_VALUE
        for (i in 1..persistCallCount) {
            persist()
        }
        return stateMachine.id
    }

    @Suspendable
    fun persist() {
        @Suppress("UNUSED_VARIABLE")
        val unusedVar = Constants.IN_PERSIST_VALUE
        persistFlowStackSnapshot()
    }
}

/**
 * Flow that tests whether the serialization works correctly.
 */
@StartableByRPC
@InitiatingFlow
class FlowStackSnapshotSerializationTestingFlow : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        flowStackSnapshot()
        val mySession = initiateFlow(ourIdentity)
        mySession.sendAndReceive<String>("Ping")
    }
}

@InitiatedBy(FlowStackSnapshotSerializationTestingFlow::class)
class DummyFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val message = otherSideSession.receive<String>()
        otherSideSession.send("$message Pong")
    }
}

fun readFlowStackSnapshotFromDir(baseDir: Path, flowId: StateMachineRunId): FlowStackSnapshot {
    val snapshotFile = flowSnapshotDir(baseDir, flowId) / "flowStackSnapshot.json"
    return snapshotFile.read {
        JacksonSupport.createNonRpcMapper().readValue(it, FlowStackSnapshot::class.java)
    }
}

private fun flowSnapshotDir(baseDir: Path, flowId: StateMachineRunId): Path {
    return baseDir / "flowStackSnapshots" / LocalDate.now().toString() / flowId.uuid.toString()
}

fun countFilesInDir(baseDir: Path, flowId: StateMachineRunId): Int {
    return flowSnapshotDir(baseDir, flowId).useDirectoryEntries { it.count() }
}

fun assertFrame(expectedMethod: String, expectedEmpty: Boolean, frame: StackSnapshotFrame) {
    assertEquals(expectedMethod, frame.method)
    assertEquals(expectedEmpty, frame.dataTypes.isEmpty())
}

@Ignore("When running via gradle the Jacoco agent interferes with the quasar instrumentation process and violates tested" +
        "criteria (specifically: extra objects are introduced to the quasar stack by th Jacoco agent). You can however " +
        "run these tests via an IDE.")
class FlowStackSnapshotTest {
    @Test(timeout=300_000)
	fun `flowStackSnapshot contains full frames when methods with side effects are called`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlow<SideEffectFlow>())))).get()
            CordaRPCClient(a.rpcAddress).use(Constants.USER, Constants.PASSWORD) { connection ->
                val stackSnapshotFrames = connection.proxy.startFlow(::SideEffectFlow).returnValue.get()
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("run", false, iterator.next())
                assertFrame("call", false, iterator.next())
                assertFrame("retrieveStackSnapshot", false, iterator.next())
                assertFrame("flowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test(timeout=300_000)
	fun `flowStackSnapshot contains empty frames when methods with no side effects are called`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlow<NoSideEffectFlow>())))).get()
            CordaRPCClient(a.rpcAddress).use(Constants.USER, Constants.PASSWORD) { connection ->
                val stackSnapshotFrames = connection.proxy.startFlow(::NoSideEffectFlow).returnValue.get()
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("run", false, iterator.next())
                assertFrame("call", true, iterator.next())
                assertFrame("retrieveStackSnapshot", true, iterator.next())
                assertFrame("flowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test(timeout=300_000)
	fun `persistFlowStackSnapshot persists empty frames to a file when methods with no side effects are called`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlow<PersistingNoSideEffectFlow>())))).get()
            CordaRPCClient(a.rpcAddress).use(Constants.USER, Constants.PASSWORD) { connection ->
                val flowId = connection.proxy.startFlow(::PersistingNoSideEffectFlow).returnValue.get()
                val snapshotFromFile = readFlowStackSnapshotFromDir(a.baseDirectory, flowId)
                val stackSnapshotFrames = convertToStackSnapshotFrames(snapshotFromFile)
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("call", true, iterator.next())
                assertFrame("persist", true, iterator.next())
                assertFrame("persistFlowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test(timeout=300_000)
	fun `persistFlowStackSnapshot persists multiple snapshots in different files`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlow<MultiplePersistingSideEffectFlow>())))).get()

            CordaRPCClient(a.rpcAddress).use(Constants.USER, Constants.PASSWORD) { connection ->
                val numberOfFlowSnapshots = 5
                val flowId = connection.proxy.startFlow(::MultiplePersistingSideEffectFlow, 5).returnValue.get()
                val fileCount = countFilesInDir(a.baseDirectory, flowId)
                assertEquals(numberOfFlowSnapshots, fileCount)
            }
        }
    }

    @Test(timeout=300_000)
	fun `flowStackSnapshot object is serializable`() {
        val mockNet = MockNetwork(emptyList(), threadPerNode = true)
        val node = mockNet.createPartyNode()
        node.registerInitiatedFlow(DummyFlow::class.java)
        node.startFlow(FlowStackSnapshotSerializationTestingFlow()).get()
        val thrown = try {
            // Due to the [MockNetwork] implementation, the easiest way to trigger object serialization process is at
            // the network stopping stage.
            mockNet.stopNodes()
            null
        } catch (exception: Exception) {
            exception
        }
        assertNull(thrown)
    }

    @Test(timeout=300_000)
	fun `persistFlowStackSnapshot stack traces are aligned with stack objects`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlow<PersistingSideEffectFlow>())))).get()

            CordaRPCClient(a.rpcAddress).use(Constants.USER, Constants.PASSWORD) { connection ->
                val flowId = connection.proxy.startFlow(::PersistingSideEffectFlow).returnValue.get()
                val snapshotFromFile = readFlowStackSnapshotFromDir(a.baseDirectory, flowId)
                var inCallCount = 0
                var inPersistCount = 0
                snapshotFromFile.stackFrames.forEach { frame ->
                    val trace = frame.stackTraceElement
                    frame.stackObjects.forEach {
                        when (it) {
                            Constants.IN_CALL_VALUE -> {
                                assertEquals(PersistingSideEffectFlow::call.name, trace.methodName)
                                inCallCount++
                            }
                            Constants.IN_PERSIST_VALUE -> {
                                assertEquals(PersistingSideEffectFlow::persist.name, trace.methodName)
                                inPersistCount++
                            }
                        }
                    }
                }
                assertTrue(inCallCount > 0)
                assertTrue(inPersistCount > 0)
            }
        }
    }
}
