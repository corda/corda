package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@StartableByRPC
class DumpingFlow : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        val name = unnecessarilyElaborateWayOfRetrievingTheNameOfThisFunction()
        Fiber.park(1, TimeUnit.NANOSECONDS) // to trigger instrumentation of this function call
        return name
    }

    @Suspendable
    fun unnecessarilyElaborateWayOfRetrievingTheNameOfThisFunction(): String {
        val stackDump = debugStackDump()
        val callOffset = 3 // 3 frames from this frame to the park in debugStackDump
        return stackDump.stackFrames[callOffset].stackTraceElement!!.methodName
    }
}


class FlowStackDumpTest {
    @Test
    fun `stackDump contains correct StackTraceElements`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlowPermission<DumpingFlow>())))).get()

            a.rpcClientToNode().use("A", "A") { connection ->
                val functionName = connection.proxy.startFlow(::DumpingFlow).returnValue.get()
                assertEquals(DumpingFlow::unnecessarilyElaborateWayOfRetrievingTheNameOfThisFunction.name, functionName)
            }
        }
    }
}
