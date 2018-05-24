package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.node.User
import org.junit.Before
import org.junit.Test
import java.lang.management.ManagementFactory
import java.sql.SQLException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class FlowRetryTest {
    @Before
    fun resetCounters() {
        InitiatorFlow.seen.clear()
        InitiatedFlow.seen.clear()
    }

    @Test
    fun `flows continue despite errors`() {
        val numSessions = 2
        val numIterations = 10
        val user = User("mark", "dadada", setOf(Permissions.startFlow<InitiatorFlow>()))
        val result: Any? = driver(DriverParameters(isDebug = true, startNodesInProcess = isQuasarAgentSpecified(),
                portAllocation = RandomFree)) {

            val nodeAHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(rpcUsers = listOf(user)).getOrThrow()

            val result = CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorFlow, numSessions, numIterations, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            }
            result
        }
        assertNotNull(result)
        assertEquals("$numSessions:$numIterations", result)
    }
}

fun isQuasarAgentSpecified(): Boolean {
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    return jvmArgs.any { it.startsWith("-javaagent:") && it.contains("quasar") }
}

class ExceptionToCauseRetry : SQLException("deadlock")

@StartableByRPC
@InitiatingFlow
class InitiatorFlow(private val sessionsCount: Int, private val iterationsCount: Int, private val other: Party) : FlowLogic<Any>() {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)

        val seen = Collections.synchronizedSet(HashSet<Visited>())

        fun visit(sessionNum: Int, iterationNum: Int, step: Step) {
            val visited = Visited(sessionNum, iterationNum, step)
            if (visited !in seen) {
                seen += visited
                throw ExceptionToCauseRetry()
            }
        }
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): Any {
        progressTracker.currentStep = FIRST_STEP
        var received: Any? = null
        visit(-1, -1, Step.First)
        for (sessionNum in 1..sessionsCount) {
            visit(sessionNum, -1, Step.BeforeInitiate)
            val session = initiateFlow(other)
            visit(sessionNum, -1, Step.AfterInitiate)
            session.send(SessionInfo(sessionNum, iterationsCount))
            visit(sessionNum, -1, Step.AfterInitiateSendReceive)
            for (iteration in 1..iterationsCount) {
                visit(sessionNum, iteration, Step.BeforeSend)
                logger.info("A Sending $sessionNum:$iteration")
                session.send("$sessionNum:$iteration")
                visit(sessionNum, iteration, Step.AfterSend)
                received = session.receive<Any>().unwrap { it }
                visit(sessionNum, iteration, Step.AfterReceive)
                logger.info("A Got $sessionNum:$iteration")
            }
            doSleep()
        }
        return received!!
    }

    // This non-flow-friendly sleep triggered a bug with session end messages and non-retryable checkpoints.
    private fun doSleep() {
        Thread.sleep(2000)
    }
}

@InitiatedBy(InitiatorFlow::class)
class InitiatedFlow(val session: FlowSession) : FlowLogic<Any>() {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)

        val seen = Collections.synchronizedSet(HashSet<Visited>())

        fun visit(sessionNum: Int, iterationNum: Int, step: Step) {
            val visited = Visited(sessionNum, iterationNum, step)
            if (visited !in seen) {
                seen += visited
                throw ExceptionToCauseRetry()
            }
        }
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() {
        progressTracker.currentStep = FIRST_STEP
        visit(-1, -1, Step.AfterInitiate)
        val sessionInfo = session.receive<SessionInfo>().unwrap { it }
        visit(sessionInfo.sessionNum, -1, Step.AfterInitiateSendReceive)
        for (iteration in 1..sessionInfo.iterationsCount) {
            visit(sessionInfo.sessionNum, iteration, Step.BeforeReceive)
            val got = session.receive<Any>().unwrap { it }
            visit(sessionInfo.sessionNum, iteration, Step.AfterReceive)
            logger.info("B Got $got")
            logger.info("B Sending $got")
            visit(sessionInfo.sessionNum, iteration, Step.BeforeSend)
            session.send(got)
            visit(sessionInfo.sessionNum, iteration, Step.AfterSend)
        }
    }
}

@CordaSerializable
data class SessionInfo(val sessionNum: Int, val iterationsCount: Int)

enum class Step { First, BeforeInitiate, AfterInitiate, AfterInitiateSendReceive, BeforeSend, AfterSend, BeforeReceive, AfterReceive }

data class Visited(val sessionNum: Int, val iterationNum: Int, val step: Step)