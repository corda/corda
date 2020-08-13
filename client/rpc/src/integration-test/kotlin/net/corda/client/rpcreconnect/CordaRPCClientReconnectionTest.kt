package net.corda.client.rpcreconnect

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCClientTest
import net.corda.client.rpc.GracefulReconnect
import net.corda.client.rpc.MaxRpcRetryException
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.UnrecoverableRPCException
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.startFlowWithClientId
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions
import net.corda.nodeapi.exceptions.RejectedCommandException
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.rpcDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.lang.Thread.sleep
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientReconnectionTest {

    private val portAllocator = incrementalPortAllocation()

    private val gracefulReconnect = GracefulReconnect()
    private val config = CordaRPCClientConfiguration.DEFAULT.copy(
            connectionRetryInterval = Duration.ofSeconds(1),
            connectionRetryIntervalMultiplier = 1.0
    )

    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    }

    @Test(timeout=300_000)
    fun `rpc node start when FlowsDrainingModeEnabled throws RejectedCommandException and won't attempt to reconnect`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress,
                    config.copy(maxReconnectAttempts = 1))

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                rpcOps.setFlowsDrainingModeEnabled(true)

                assertThatThrownBy { rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get() }
                        .isInstanceOf(RejectedCommandException::class.java).hasMessage("Node is draining before shutdown. Cannot start new flows through RPC.")
            }
        }
    }


    @Test(timeout=300_000)
    fun `minimum server protocol version should cause exception if higher than allowed`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            assertThatThrownBy {
                val node = startNode ()
                val client = CordaRPCClient(node.rpcAddress, config.copy(minimumServerProtocolVersion = 100, maxReconnectAttempts = 1))
                client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)
            }
                    .isInstanceOf(UnrecoverableRPCException::class.java)
                    .hasMessageStartingWith("Requested minimum protocol version (100) is higher than the server's supported protocol version ")
        }
    }

    @Test(timeout=300_000)
    fun `rpc client calls and returned observables continue working when the server crashes and restarts`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val latch = CountDownLatch(2)
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val networkParameters = rpcOps.networkParameters
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode()

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                val networkParametersAfterCrash = rpcOps.networkParameters
                assertThat(networkParameters).isEqualTo(networkParametersAfterCrash)
                assertTrue {
                    latch.await(20, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test(timeout=300_000)
    fun `a client can successfully unsubscribe a reconnecting observable`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val latch = CountDownLatch(2)
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                val subscription = cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode()

                subscription.unsubscribe()

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                assertFalse {
                    latch.await(4, TimeUnit.SECONDS)
                }
            }

        }
    }

    @Test(timeout=300_000)
    fun `rpc client calls and returned observables continue working when there is failover between servers`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val latch = CountDownLatch(2)

            fun startNode(address: NetworkHostAndPort): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val addresses = listOf(NetworkHostAndPort("localhost", portAllocator.nextPort()), NetworkHostAndPort("localhost", portAllocator.nextPort()))

            val node = startNode(addresses[0])
            val client = CordaRPCClient(addresses, config)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val networkParameters = rpcOps.networkParameters
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                cashStatesFeed.updates.subscribe { latch.countDown() }
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                node.stop()
                startNode(addresses[1])

                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                val networkParametersAfterCrash = rpcOps.networkParameters
                assertThat(networkParameters).isEqualTo(networkParametersAfterCrash)
                assertTrue {
                    latch.await(2, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test(timeout=300_000)
    fun `when user code throws an error on a reconnecting observable, then onError is invoked and observable is unsubscribed successfully`() {
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val normalLatch = CountDownLatch(1)
            val errorLatch = CountDownLatch(1)
            var observedEvents = 0

            fun startNode(address: NetworkHostAndPort): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val addresses = listOf(NetworkHostAndPort("localhost", portAllocator.nextPort()), NetworkHostAndPort("localhost", portAllocator.nextPort()))

            startNode(addresses[0])
            val client = CordaRPCClient(addresses)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val cashStatesFeed = rpcOps.vaultTrack(Cash.State::class.java)
                val subscription = cashStatesFeed.updates.subscribe ({
                    normalLatch.countDown()
                    observedEvents++
                    throw RuntimeException()
                }, {
                    errorLatch.countDown()
                })
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()
                rpcOps.startTrackedFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.get()

                assertTrue { normalLatch.await(2, TimeUnit.SECONDS) }
                assertTrue { errorLatch.await(2, TimeUnit.SECONDS) }
                assertThat(subscription.isUnsubscribed).isTrue()
                assertThat(observedEvents).isEqualTo(1)
            }
        }
    }

    @Test(timeout=300_000)
    fun `an RPC call fails, when the maximum number of attempts is exceeded`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = GracefulReconnect(maxAttempts = 1))).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps

                node.stop()
                thread { startNode() }
                assertThatThrownBy { rpcOps.networkParameters }
                        .isInstanceOf(MaxRpcRetryException::class.java)
            }

        }
    }

    @Test(timeout=300_000)
    fun `establishing an RPC connection fails if there is no node listening to the specified address`() {
        rpcDriver {
            assertThatThrownBy {
                CordaRPCClient(NetworkHostAndPort("localhost", portAllocator.nextPort()), config)
                        .start(rpcUser.username, rpcUser.password, GracefulReconnect())
            }.isInstanceOf(RPCException::class.java)
                    .hasMessage("Cannot connect to server(s). Tried with all available servers.")
        }
    }

    @Test(timeout=300_000)
    fun `RPC connection can be shut down after being disconnected from the node`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            CordaRPCClient(node.rpcAddress, config).start(rpcUser.username, rpcUser.password, gracefulReconnect).use {
                node.stop()
                thread {
                    it.proxy.startTrackedFlow(
                            ::CashIssueFlow,
                            10.DOLLARS,
                            OpaqueBytes.of(0),
                            defaultNotaryIdentity
                    )
                }
                // This just gives the flow time to get started so the RPC detects a problem
                sleep(1000)
                it.close()
            }
        }
    }

    @Test(timeout=300_000)
    fun `RPC connection stops reconnecting after config number of retries`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            val conf = config.copy(maxReconnectAttempts = 2)
            fun startNode(): NodeHandle = startNode(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                    customOverrides = mapOf("rpcSettings.address" to address.toString())
            ).getOrThrow()

            val node = startNode()
            val connection = CordaRPCClient(node.rpcAddress, conf).start(rpcUser.username, rpcUser.password, gracefulReconnect)
            node.stop()
            // After two tries we throw RPCException
            assertThatThrownBy { connection.proxy.isWaitingForShutdown() }
                    .isInstanceOf(RPCException::class.java)
        }
    }

    @Test(timeout=300_000)
    fun `rpc client does not attempt to reconnect after shutdown`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)
            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                rpcOps.shutdown()
                // If we get here we know we're not stuck in a reconnect cycle with a node that's been shut down
                assertThat(rpcOps.reconnectingRPCConnection.isClosed())
            }
        }
    }

    @Test(timeout=300_000)
    fun `rpc returned flow -started with cient id- result future continue working when the node crashes and restarts`() {
        driver(DriverParameters(inMemoryDB = false, cordappsForAllNodes = listOf(this.enclosedCordapp()))) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                    customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)
            client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val clientId = UUID.randomUUID().toString()
                // assert result reconnectable futures returned work from both 'startFlowWithClientId' and 'reattachFlowWithClientId'
                val flowHandle0 = rpcOps.startFlowWithClientId(clientId, ::SimpleFlow)
                val flowHandle1 = rpcOps.reattachFlowWithClientId<Int>(clientId)

                val completedCounter = AtomicInteger(0)
                flowHandle0.returnValue.doOnComplete {
                    completedCounter.incrementAndGet()
                }
                flowHandle1!!.returnValue.doOnComplete {
                    completedCounter.incrementAndGet()
                }

                flowHandle0.returnValue.thenMatch({
                    completedCounter.incrementAndGet()
                }, {})
                flowHandle1.returnValue.thenMatch({
                    completedCounter.incrementAndGet()
                }, {})

                flowHandle0.returnValue.toCompletableFuture().thenApply {
                    completedCounter.incrementAndGet()
                }
                flowHandle1.returnValue.toCompletableFuture().thenApply {
                    completedCounter.incrementAndGet()
                }

                node.stop()
                thread {
                    sleep(1000)
                    startNode()
                }

                var result1: Int? = null
                thread {
                    result1 = flowHandle1.returnValue.get()
                }

                val result0 = flowHandle0.returnValue.get()

                sleep(1000)
                assertEquals(6, completedCounter.get())
                assertEquals(5, result0!!)
                assertEquals(5, result1!!)
                assertThat(rpcOps.reconnectingRPCConnection.isClosed())
            }
        }
    }

    @Test(timeout=300_000)
    fun `rpc returned flow -started with cient id- exception future continue working when the node crashes and restarts`() {
        driver(DriverParameters(inMemoryDB = false, cordappsForAllNodes = listOf(this.enclosedCordapp()))) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)
            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val clientId = UUID.randomUUID().toString()
                val flowHandle = rpcOps.startFlowWithClientId(clientId, ::ThrowingFlow)

                var erroredCounter = 0
                flowHandle.returnValue.doOnError {
                    erroredCounter++
                }

                flowHandle.returnValue.toCompletableFuture().exceptionally {
                    erroredCounter++
                }

                node.stop()
                thread {
                    sleep(1000)
                    startNode()
                }

                assertFailsWith<CordaRuntimeException> {
                    flowHandle.returnValue.getOrThrow()
                }

                sleep(1000)
                assertEquals(2, erroredCounter)
                assertThat(rpcOps.reconnectingRPCConnection.isClosed())
            }
        }
    }

    @Test(timeout=300_000)
    fun `rpc re attach to flow with client id tries to reconnect when the node is down`() {
        driver(DriverParameters(inMemoryDB = false, cordappsForAllNodes = listOf(this.enclosedCordapp()))) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)
            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val clientId = UUID.randomUUID().toString()
                rpcOps.startFlowWithClientId(clientId, ::SimpleFlow)

                node.stop()
                thread {
                    sleep(1000)
                    startNode()
                }
                val flowHandle = rpcOps.reattachFlowWithClientId<Int>(clientId)

                val result = flowHandle!!.returnValue.get()

                assertEquals(5, result!!)
                assertThat(rpcOps.reconnectingRPCConnection.isClosed())
            }
        }
    }

    @Test(timeout=300_000)
    fun `rpc remove client id tries to reconnect when the node is down`() {
        driver(DriverParameters(inMemoryDB = false, cordappsForAllNodes = listOf(this.enclosedCordapp()))) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)
            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = gracefulReconnect)).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps
                val clientId = UUID.randomUUID().toString()
                rpcOps.startFlowWithClientId(clientId, ::SimpleFlow).returnValue.getOrThrow()

                node.stop()
                thread {
                    sleep(1000)
                    startNode()
                }
                val removed = rpcOps.removeClientId(clientId)

                assertTrue(removed)
                assertThat(rpcOps.reconnectingRPCConnection.isClosed())
            }
        }
    }

    @StartableByRPC
    class SimpleFlow : FlowLogic<Int>() {

        @Suspendable
        override fun call(): Int {
            sleep(10.seconds)
            return 5
        }
    }

    @StartableByRPC
    class ThrowingFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            sleep(10.seconds)
            throw IllegalStateException("bye")
        }
    }
}