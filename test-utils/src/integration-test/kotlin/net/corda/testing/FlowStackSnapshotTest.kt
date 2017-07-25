package net.corda.testing

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.driver.driver
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@CordaSerializable
data class StackSnapshotFrame(val method: String, val clazz: String, val dataTypes: List<String?>, val flowId: String? = null)

/*
 * Calculates the count of full and empty frames. We consider frame to be empty if there is no stack data
 * associated with it (i.e. the stackObjects is an empty list). Otherwise (i.e. when the stackObjects is not
 * an empty list the frame is considered to be full. */
fun convertToStackSnapshotFrames(snapshot: FlowStackSnapshot): List<StackSnapshotFrame> {
    return snapshot.stackFrames.map {
        val dataTypes = it.stackObjects.map {
            if (it == null) null else it::class.qualifiedName
        }
        val stackTraceElement = it.stackTraceElement!!
        StackSnapshotFrame(stackTraceElement.methodName, stackTraceElement.className, dataTypes)
    }
}

/*
 * Flow that during its execution performs calls with side effects in terms of Quasar. The presence of
 * side effect calls drives Quasar decision on stack optimisation application. The stack optimisation method aims
 * to reduce the amount of data stored on Quasar stack to minimum and is based on static code analyses performed during
 * the code instrumentation phase, during which Quasar checks if a method performs side effect calls. If not,
 * the method is annotated to be optimised, meaning that none of its local variables are stored on the stack and
 * during the runtime the method can be replayed with a guarantee to be idempotent.
 */
@StartableByRPC
class SideEffectFlow : FlowLogic<List<StackSnapshotFrame>>() {
    var sideEffectField = ""

    @Suspendable
    override fun call(): List<StackSnapshotFrame> {
        sideEffectField = "sideEffectInCall"
        // Expected to be on stack
        val unusedVar = Constants.IN_CALL_VALUE
        val numberOfFullFrames = retrieveStackSnapshot()
        return numberOfFullFrames
    }

    @Suspendable
    fun retrieveStackSnapshot(): List<StackSnapshotFrame> {
        sideEffectField = "sideEffectInRetrieveStackSnapshot"
        // Expected to be on stack
        val unusedVar = Constants.IN_RETRIEVE_STACK_SNAPSHOT_VALUE
        val snapshot = flowStackSnapshot()
        return convertToStackSnapshotFrames(snapshot)
    }

}

/*
 * Flow that during its execution performs calls with no side effects in terms of Quasar.
 * Thus empty frames are expected on in the stack snapshot as Quasar will optimise.
 */
@StartableByRPC
class NoSideEffectFlow : FlowLogic<List<StackSnapshotFrame>>() {

    @Suspendable
    override fun call(): List<StackSnapshotFrame> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inCall"
        val numberOfFullFrames = retrieveStackSnapshot()
        return numberOfFullFrames
    }

    @Suspendable
    fun retrieveStackSnapshot(): List<StackSnapshotFrame> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inRetrieveStackSnapshot"
        val flowStackDump = flowStackSnapshot()
        return convertToStackSnapshotFrames(flowStackDump)
    }
}

object Constants {
    val IN_PERSIST_VALUE = "inPersist"
    val IN_CALL_VALUE = "inCall"
    val IN_RETRIEVE_STACK_SNAPSHOT_VALUE = "inRetrieveStackSnapshot"
    val USER = "User"
    val PASSWORD = "Password"

}

/*
 * No side effect flow that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingNoSideEffectFlow : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inCall"
        persist()
        return stateMachine.id.toString()
    }

    @Suspendable
    fun persist() {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inPersist"
        persistFlowStackSnapshot()
    }
}

/*
 * Flow with side effects that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingSideEffectFlow : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val unusedVar = Constants.IN_CALL_VALUE
        persist()
        return stateMachine.id.toString()
    }

    @Suspendable
    fun persist() {
        val unusedVar = Constants.IN_PERSIST_VALUE
        persistFlowStackSnapshot()
    }
}

fun readFlowStackSnapshotFromDir(baseDir: Path, flowId: String): FlowStackSnapshot {
    val snapshotFile = File(baseDir.toFile(), "flowStackSnapshots/${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}/$flowId/flowStackSnapshot.json")
    return ObjectMapper().readValue(snapshotFile.inputStream(), FlowStackSnapshot::class.java)
}

fun assertFrame(expectedMethod: String, expectedEmpty: Boolean, frame: StackSnapshotFrame) {
    assertEquals(expectedMethod, frame.method)
    assertEquals(expectedEmpty, frame.dataTypes.isEmpty())
}

class FlowStackSnapshotTest {

    @Test
    fun `flowStackSnapshot contains full frames when methods with side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<SideEffectFlow>())))).get()
            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val stackSnapshotFrames = connection.proxy.startFlow(::SideEffectFlow).returnValue.get()
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("run", false, iterator.next())
                assertFrame("call", false, iterator.next())
                assertFrame("retrieveStackSnapshot", false, iterator.next())
                assertFrame("flowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test
    fun `flowStackSnapshot contains empty frames when methods with no side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<NoSideEffectFlow>())))).get()
            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val stackSnapshotFrames = connection.proxy.startFlow(::NoSideEffectFlow).returnValue.get()
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("run", false, iterator.next())
                assertFrame("call", true, iterator.next())
                assertFrame("retrieveStackSnapshot", true, iterator.next())
                assertFrame("flowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test
    fun `persistFlowStackSnapshot persists empty frames to a file when methods with no side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<PersistingNoSideEffectFlow>())))).get()

            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val flowId = connection.proxy.startFlow(::PersistingNoSideEffectFlow).returnValue.get()
                val snapshotFromFile = readFlowStackSnapshotFromDir(a.configuration.baseDirectory, flowId)
                val stackSnapshotFrames = convertToStackSnapshotFrames(snapshotFromFile)
                val iterator = stackSnapshotFrames.listIterator()
                assertFrame("call", true, iterator.next())
                assertFrame("persist", true, iterator.next())
                assertFrame("persistFlowStackSnapshot", false, iterator.next())
            }
        }
    }

    @Test
    fun `persistFlowStackSnapshot stack traces are aligned with stack objects`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<PersistingSideEffectFlow>())))).get()

            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val flowId = connection.proxy.startFlow(::PersistingSideEffectFlow).returnValue.get()
                val snapshotFromFile = readFlowStackSnapshotFromDir(a.configuration.baseDirectory, flowId)
                var inCallCount = 0
                var inPersistCount = 0
                snapshotFromFile.stackFrames.forEach {
                    val trace = it.stackTraceElement
                    it.stackObjects.forEach {
                        when (it) {
                            Constants.IN_CALL_VALUE -> {
                                assertEquals(PersistingSideEffectFlow::call.name, trace!!.methodName)
                                inCallCount++
                            }
                            Constants.IN_PERSIST_VALUE -> {
                                assertEquals(PersistingSideEffectFlow::persist.name, trace!!.methodName)
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
