package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class HardRestartTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_BANK_B_NAME.toDatabaseSchemaName(),
                DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }
    @StartableByRPC
    @InitiatingFlow
    class Ping(val pongParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val pongSession = initiateFlow(pongParty)
            pongSession.sendAndReceive<Unit>(Unit)
        }
    }

    @InitiatedBy(Ping::class)
    class Pong(val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            pingSession.sendAndReceive<Unit>(Unit)
        }
    }

    @Test
    fun restartPingPongFlowRandomly() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        driver(DriverParameters(isDebug = true, startNodesInProcess = false)) {
            val (a, b) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:30000")),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            ).transpose().getOrThrow()

            val latch = CountDownLatch(1)

            // We kill -9 and restart the Pong node after a random sleep
            val pongRestartThread = thread {
                latch.await()
                val ms = Random().nextInt(1000)
                println("Sleeping $ms ms before kill")
                Thread.sleep(ms.toLong())
                (b as OutOfProcess).process.destroyForcibly()
                b.stop()
                startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            }
            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                val returnValue = it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity()).returnValue
                latch.countDown()
                // No matter the kill
                returnValue.getOrThrow()
            }


            pongRestartThread.join()
        }
    }

    sealed class RecursiveMode {
        data class Top(val otherParty: Party, val initialDepth: Int) : RecursiveMode()
        data class Recursive(val otherSession: FlowSession) : RecursiveMode()
    }

    @StartableByRPC
    @InitiatingFlow
    @InitiatedBy(RecursiveB::class)
    class RecursiveA(val mode: RecursiveMode) : FlowLogic<String>() {
        constructor(otherSession: FlowSession) : this(RecursiveMode.Recursive(otherSession))
        constructor(otherParty: Party, initialDepth: Int) : this(RecursiveMode.Top(otherParty, initialDepth))
        @Suspendable
        override fun call(): String {
            return when (mode) {
                is HardRestartTest.RecursiveMode.Top -> {
                    val session = initiateFlow(mode.otherParty)
                    session.sendAndReceive<String>(mode.initialDepth).unwrap { it }
                }
                is HardRestartTest.RecursiveMode.Recursive -> {
                    val depth = mode.otherSession.receive<Int>().unwrap { it }
                    val string = if (depth > 0) {
                        val newSession = initiateFlow(mode.otherSession.counterparty)
                        newSession.sendAndReceive<String>(depth).unwrap { it }
                    } else {
                        "-"
                    }
                    mode.otherSession.send(string)
                    string
                }
            }
        }
    }

    @InitiatingFlow
    @InitiatedBy(RecursiveA::class)
    class RecursiveB(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val depth = otherSession.receive<Int>().unwrap { it }
            val newSession = initiateFlow(otherSession.counterparty)
            val string = newSession.sendAndReceive<String>(depth - 1).unwrap { it }
            otherSession.send(string + ":" + depth)
        }
    }

    @Test
    fun restartRecursiveFlowRandomly() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<RecursiveA>(), Permissions.all()))
        driver(DriverParameters(isDebug = true, startNodesInProcess = false)) {
            val (a, b) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:30000")),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            ).transpose().getOrThrow()

            val latch = CountDownLatch(1)

            // We kill -9 and restart the node B after a random sleep
            val bRestartThread = thread {
                latch.await()
                val ms = Random().nextInt(1000)
                println("Sleeping $ms ms before kill")
                Thread.sleep(ms.toLong())
                (b as OutOfProcess).process.destroyForcibly()
                b.stop()
                startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            }
            val executor = Executors.newFixedThreadPool(8)
            try {
                val tlRpc = ThreadLocal<CordaRPCOps>()
                (1 .. 10).map { num ->
                    executor.fork {
                        val rpc = tlRpc.get() ?: CordaRPCClient(a.rpcAddress).start(demoUser.username, demoUser.password).proxy.also { tlRpc.set(it) }
                        val string = rpc.startFlow(::RecursiveA, b.nodeInfo.singleIdentity(), 10).returnValue.getOrThrow()
                        latch.countDown()
                        println("$num: $string")
                    }
                }.transpose().getOrThrow()

                bRestartThread.join()
            } finally {
                executor.shutdown()
            }
        }
    }
}
