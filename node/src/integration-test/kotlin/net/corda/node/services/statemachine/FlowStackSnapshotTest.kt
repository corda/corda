package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.driver.driver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
* Calculates the count of full and empty frames. We consider frame to be empty if there is no stack data
* associated with it (i.e. the stackObjects is an empty list). Otherwise (i.e. when the stackObjects is not
* an empty list the frame is considered to be full. */
fun numberOfFullEmptyFrames(snapshot: FlowStackSnapshot):Pair<Int, Int> {
    var fullFramesCount = snapshot.stackFrames.size;
    snapshot.stackFrames.forEach { if (it.stackObjects == null || it.stackObjects.isEmpty()) fullFramesCount-- }
    return fullFramesCount to snapshot.stackFrames.size - fullFramesCount
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
class SideEffectFlow : FlowLogic<Pair<Int, Int>>() {
    var sideEffectField = ""

    @Suspendable
    override fun call(): Pair<Int, Int> {
        sideEffectField = "sideEffectInCall"
        // Expected to be on stack
        val unusedVar = Constants.IN_CALL_VALUE
        val numberOfFullFrames = getNumberOfFullFrames()
        return numberOfFullFrames
    }

    @Suspendable
    fun getNumberOfFullFrames(): Pair<Int, Int> {
        sideEffectField = "sideEffectInGetNumberOfFullFrames"
        // Expected to be on stack
        val unusedVar = Constants.IN_GET_NUMBER_OF_FULL_FRAMES_VALUE
        val snapshot = flowStackSnapshot()
        return numberOfFullEmptyFrames(snapshot)
    }

}

/*
 * Flow that during its execution performs calls with side effects in terms of Quasar.
 * Thus empty frames are expected on in the stack snapshot as Quasar will optimise.
 */
@StartableByRPC
class NoSideEffectFlow : FlowLogic<Pair<Int, Int>>() {

    @Suspendable
    override fun call(): Pair<Int, Int> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inCall"
        val numberOfFullFrames = getNumberOfFullFrames()
        return numberOfFullFrames
    }

    @Suspendable
    fun getNumberOfFullFrames(): Pair<Int, Int> {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inGetNumberOfFullFrames"
        val flowStackDump = flowStackSnapshot()
        return numberOfFullEmptyFrames(flowStackDump)
    }
}

object Constants {
    val IN_PERSIST_VALUE = "inPersist"
    val IN_CALL_VALUE = "inCall"
    val IN_GET_NUMBER_OF_FULL_FRAMES_VALUE = "inGetNumberOfFullFrames"
    val USER = "User"
    val PASSWORD = "Password"

}

/*
 * No side effect flow that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingNoSideEffectFlow(val path: String) : FlowLogic<Unit> () {

    @Suspendable
    override fun call() {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inCall"
        persist()
    }

    @Suspendable
    fun persist() {
        // Using the [Constants] object here is considered by Quasar as a side effect. Thus explicit initialization
        val unusedVar = "inPersist"
        persistFlowStackSnapshot(path)
    }
}

/*
 * Flow with side effects that stores the partial snapshot into a file, path to which is passed in the flow constructor.
 */
@StartableByRPC
class PersistingSideEffectFlow(val path: String) : FlowLogic<Unit> () {

    @Suspendable
    override fun call() {
        val unusedVar = Constants.IN_CALL_VALUE
        persist()
    }

    @Suspendable
    fun persist() {
        val unusedVar = Constants.IN_PERSIST_VALUE
        persistFlowStackSnapshot(path)
    }
}


class FlowStackDumpTest {

    @Rule @JvmField val tempDir = TemporaryFolder()

    @Test
    fun `flowStackSnapshot contains full frames when methods with side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<SideEffectFlow>())))).get()

            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val (fullFramesCount, emptyFramesCount) = connection.proxy.startFlow(::SideEffectFlow).returnValue.get()
                // The total amount of frames was derived from inspecting (in the debug mode) Quasar stack state.
                assertEquals(5, fullFramesCount)
                assertEquals(0, emptyFramesCount)
            }
        }
    }

    @Test
    fun `flowStackSnapshot contains empty frames when methods with no side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<NoSideEffectFlow>())))).get()

            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                val (fullFramesCount, emptyFramesCount) = connection.proxy.startFlow(::NoSideEffectFlow).returnValue.get()
                // The total amount of frames was derived from inspecting (in the debug mode) Quasar stack state.
                assertEquals(3, fullFramesCount)
                assertEquals(2, emptyFramesCount)
            }
        }
    }

    @Test
    fun `persistFlowStackSnapshot persists empty frames to a file when methods with no side effects are called`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<PersistingNoSideEffectFlow>())))).get()
            val file = tempDir.newFile();
            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                connection.proxy.startFlow(::PersistingNoSideEffectFlow, file.absolutePath).returnValue.get()
                val snapshotFromFile = ObjectMapper().readValue(file.inputStream(), FlowStackSnapshot::class.java)
                val (fullFramesCount, emptyFramesCount) = numberOfFullEmptyFrames(snapshotFromFile)
                // The total amount of frames was derived from inspecting (in the debug mode) Quasar stack state.
                assertEquals(2, fullFramesCount)
                assertEquals(1, emptyFramesCount)
                assertTrue(snapshotFromFile.stackFrames[1].stackObjects.contains(Constants.IN_PERSIST_VALUE))
            }
        }
    }

    @Test
    fun `persistFlowStackSnapshot stack traces are aligned with stack objects`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User(Constants.USER, Constants.PASSWORD, setOf(startFlowPermission<PersistingSideEffectFlow>())))).get()
            val file = tempDir.newFile();
            a.rpcClientToNode().use(Constants.USER, Constants.PASSWORD) { connection ->
                connection.proxy.startFlow(::PersistingSideEffectFlow, file.absolutePath).returnValue.get()
                val snapshotFromFile = ObjectMapper().readValue(file.inputStream(), FlowStackSnapshot::class.java)
                val (fullFramesCount, emptyFramesCount) = numberOfFullEmptyFrames(snapshotFromFile)
                // The total amount of frames was derived from inspecting (in the debug mode) Quasar stack state.
                assertEquals(3, fullFramesCount)
                assertEquals(0, emptyFramesCount)
                snapshotFromFile.stackFrames.forEach {
                    val trace = it.stackTraceElement
                    it.stackObjects.forEach {
                        when (it) {
                            Constants.IN_CALL_VALUE -> assertEquals(PersistingSideEffectFlow::call.name, trace!!.methodName)
                            Constants.IN_PERSIST_VALUE -> assertEquals(PersistingSideEffectFlow::persist.name, trace!!.methodName)
                        }
                    }
                }
            }
        }
    }
}
