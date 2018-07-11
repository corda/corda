package net.corda.bridge

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import net.corda.testing.node.internal.internalDriver
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class BridgeRestartTest : IntegrationTest() {
    companion object {
        val pingStarted = ConcurrentHashMap<StateMachineRunId, OpenFuture<Unit>>()

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_BANK_B_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }

    @StartableByRPC
    @InitiatingFlow
    class Ping(val pongParty: Party, val times: Int) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val pongSession = initiateFlow(pongParty)
            pongSession.sendAndReceive<Unit>(times)
            pingStarted.getOrPut(runId) { openFuture() }.set(Unit)
            for (i in 1 .. times) {
                logger.info("PING $i")
                val j = pongSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @InitiatedBy(Ping::class)
    class Pong(val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val times = pingSession.sendAndReceive<Int>(Unit).unwrap { it }
            for (i in 1 .. times) {
                logger.info("PONG $i")
                val j = pingSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @Test
    fun restartLongPingPongFlowRandomly() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        internalDriver(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.bridge")) {
            val bFuture = startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            val bridgePort = 20005
            val brokerPort = 21005
            val aBridgeFuture = startBridge(DUMMY_BANK_A_NAME, bridgePort, brokerPort, mapOf(
                    "outboundConfig" to mapOf(
                            "artemisBrokerAddress" to "localhost:$brokerPort"
                    ),
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$bridgePort"
                    )
            ))


            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "p2pAddress" to "localhost:$bridgePort",
                            "messagingServerAddress" to "0.0.0.0:$brokerPort",
                            "messagingServerExternal" to false,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true
                            )
                    )
            )

            val a = aFuture.getOrThrow()
            val b = bFuture.getOrThrow()
            val aBridge = aBridgeFuture.getOrThrow()

            // We kill -9 and restart the bridge after a random sleep
            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                val handle = it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 100)

                val bridgeRestartThread = thread {
                    pingStarted.getOrPut(handle.id) { openFuture() }.getOrThrow()
                    val ms = Random().nextInt(5000)
                    println("Sleeping $ms ms before kill")
                    Thread.sleep(ms.toLong())
                    bounceBridge(aBridge)
                }

                handle.returnValue.getOrThrow()
                bridgeRestartThread.join()
            }

        }
    }

    @Test
    fun restartSeveralPingPongFlowsRandomly() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        internalDriver(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.bridge")) {
            val bFuture = startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:40000"))
            val bridgePort = 20005
            val brokerPort = 21005
            val aBridgeFuture = startBridge(DUMMY_BANK_A_NAME, bridgePort, brokerPort, mapOf(
                    "outboundConfig" to mapOf(
                            "artemisBrokerAddress" to "localhost:$brokerPort"
                    ),
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$bridgePort"
                    )
            ))


            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "p2pAddress" to "localhost:$bridgePort",
                            "messagingServerAddress" to "0.0.0.0:$brokerPort",
                            "messagingServerExternal" to false,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true
                            )
                    )
            )

            val a = aFuture.getOrThrow()
            val b = bFuture.getOrThrow()
            val aBridge = aBridgeFuture.getOrThrow()

            // We kill -9 and restart the bridge after a random sleep
            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) { connection ->
                val handles = (1 .. 10).map {
                    connection.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 100)
                }

                val bridgeRestartThread = thread(isDaemon = true) {
                    //pingStarted.getOrPut(handle.id) { openFuture() }.getOrThrow()
                    val ms = Random().nextInt(5000)
                    println("Sleeping $ms ms before kill")
                    Thread.sleep(ms.toLong())
                    bounceBridge(aBridge)
                }

                for (handle in handles) {
                    handle.returnValue.getOrThrow()
                }
                bridgeRestartThread.join()
            }
        }
    }
}