package net.corda.client.rpc

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.ext.RPCConnectionListener
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.core.utilities.threadDumpAsString
import net.corda.testing.common.internal.eventually
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.internal.rpcDriver
import net.corda.testing.node.internal.rpcTestUser
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class RPCConnectionListenerTest(@Suppress("unused") private val iteration: Int) {

    companion object {
        private val logger = contextLogger()

        @JvmStatic
        @Parameterized.Parameters(name = "iteration = {0}")
        fun iterations(): Iterable<Array<Int>> {
            // It is possible to change this value to a greater number
            // to ensure that the test is not flaking when executed on CI
            val repsCount = 1
            return (1..repsCount).map { arrayOf(it) }
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    interface StringRPCOps : RPCOps {
        fun stringTestMethod(): String
    }

    private object StringRPCOpsImpl : StringRPCOps {

        const val testPhrase = "I work with Strings."

        override val protocolVersion = 1000

        override fun stringTestMethod(): String = testPhrase
    }

    @Test(timeout = 300_000)
    fun `basic listener scenario`() {
        rpcDriver {
            val server = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()

            val listener = mock<RPCConnectionListener<StringRPCOps>>()

            // Establish connection and exchange some calls
            val (rpcConnection, _) = startRpcClient(StringRPCOps::class.java, server.broker.hostAndPort!!,
                    listeners = listOf(listener)).get()
            verify(listener, times(1)).onConnect(any())
            val proxy = rpcConnection.proxy
            assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())

            whenever(listener.onDisconnect(any())).then {
                @Suppress("unchecked_cast")
                val context = it.arguments[0] as RPCConnectionListener.ConnectionContext<StringRPCOps>
                assertSame(rpcConnection, context.connectionOpt)
            }

            // Shutdown server
            server.shutdown()

            eventually(duration = 30.seconds) {
                verify(listener, times(1)).onDisconnect(any())
                assertThatThrownBy { proxy.stringTestMethod() }.isInstanceOf(RPCException::class.java)
            }

            verify(listener, never()).onPermanentFailure(any())
        }
    }

    @Test(timeout = 300_000)
    fun `wrong credentials`() {
        rpcDriver {
            val server = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()

            val listener = mock<RPCConnectionListener<StringRPCOps>>()
            whenever(listener.onPermanentFailure(any())).then {
                @Suppress("unchecked_cast")
                val context = it.arguments[0] as RPCConnectionListener.ConnectionContext<StringRPCOps>
                assertNull(context.connectionOpt)
                assertThat(context.throwableOpt).isInstanceOf(ActiveMQSecurityException::class.java)
            }

            // Start client with wrong credentials
            assertThatThrownBy {
                startRpcClient(StringRPCOps::class.java,
                        server.broker.hostAndPort!!,
                        username = "wrongOne",
                        listeners = listOf(listener)).get()
            }.hasCauseInstanceOf(ActiveMQSecurityException::class.java)

            verify(listener, never()).onConnect(any())
            verify(listener, never()).onDisconnect(any())
            verify(listener, times(1)).onPermanentFailure(any())

            server.rpcServer.close()
        }
    }

    @Test(timeout = 300_000)
    fun `failover listener scenario`() {
        rpcDriver {
            val primary = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()
            val secondary = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()

            val listener = mock<RPCConnectionListener<StringRPCOps>>()

            // Establish connection with HA pool passed-in and exchange some calls
            val haAddressPool = listOf(primary, secondary).map { it.broker.hostAndPort!! }
            logger.info("HA address pool: $haAddressPool")
            val (rpcConnection, _) = startRpcClient(StringRPCOps::class.java,
                    haAddressPool,
                    listeners = listOf(listener)).get()
            verify(listener, times(1)).onConnect(any())
            val proxy = rpcConnection.proxy
            assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())

            whenever(listener.onDisconnect(any())).then {
                @Suppress("unchecked_cast")
                val context = it.arguments[0] as RPCConnectionListener.ConnectionContext<StringRPCOps>
                assertSame(rpcConnection, context.connectionOpt)
            }

            // Shutdown primary
            primary.shutdown()

            eventually(duration = 30.seconds) {
                // First disconnect must happen
                verify(listener, times(1)).onDisconnect(any())
                // Followed by connect to secondary
                verify(listener, times(2)).onConnect(any())
                // Then functionality should start to work again
                assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())
            }

            // Shutdown secondary
            secondary.shutdown()

            eventually(duration = 30.seconds) {
                // Disconnect from secondary happened
                verify(listener, times(2)).onDisconnect(any())
                // Subsequent calls throw
                assertThatThrownBy { proxy.stringTestMethod() }.isInstanceOf(RPCException::class.java)
            }

            verify(listener, never()).onPermanentFailure(any())
        }
    }

    @Test(timeout = 300_000)
    fun `exceed number of retries scenario`() {
        rpcDriver {
            val primary = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()
            val secondary = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()

            val listener = mock<RPCConnectionListener<StringRPCOps>>()

            // Setup client for having a finite number of quick retries
            val fake = NetworkHostAndPort("localhost", nextPort())
            val haAddressPool = listOf(fake) + listOf(primary, secondary).map { it.broker.hostAndPort!! }
            logger.info("HA address pool: $haAddressPool")
            val (rpcConnection, _) = startRpcClient(StringRPCOps::class.java,
                    haAddressPool,
                    listeners = listOf(listener),
                    configuration = CordaRPCClientConfiguration(maxReconnectAttempts = 3, connectionRetryInterval = 1.millis)
            ).get()
            verify(listener, times(1)).onConnect(any())
            val proxy = rpcConnection.proxy
            assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())

            whenever(listener.onDisconnect(any())).then {
                @Suppress("unchecked_cast")
                val context = it.arguments[0] as RPCConnectionListener.ConnectionContext<StringRPCOps>
                assertSame(rpcConnection, context.connectionOpt)
            }

            // Shutdown primary
            primary.shutdown()

            eventually(duration = 30.seconds) {
                // Followed by connect to secondary
                verify(listener, times(2)).onConnect(any())
                // Then functionality should start to work again
                assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())
            }

            // Shutdown secondary
            secondary.shutdown()

            eventually(duration = 30.seconds) {
                // Disconnect from secondary happened
                verify(listener, times(2)).onDisconnect(any())
                // Subsequent calls throw
                assertThatThrownBy { proxy.stringTestMethod() }.isInstanceOf(RPCException::class.java)
                // Having attempted to connect multiple times - we will give up and declare the state of permanent failure
                verify(listener, times(1)).onPermanentFailure(any())
            }
        }
    }

    private class KickAndReconnectCallable constructor(private val serverControl: ActiveMQServerControl,
                                                               private val client: RPCClient<StringRPCOps>,
                                                               private val proxy: StringRPCOps) : Callable<Unit> {
        override fun call() {
            val latch = CountDownLatch(1)
            val reConnectListener = object : RPCConnectionListener<StringRPCOps> {
                override fun onConnect(context: RPCConnectionListener.ConnectionContext<StringRPCOps>) {
                    latch.countDown()
                }

                override fun onDisconnect(context: RPCConnectionListener.ConnectionContext<StringRPCOps>) {
                    logger.warn("Unexpected disconnect")
                }

                override fun onPermanentFailure(context: RPCConnectionListener.ConnectionContext<StringRPCOps>) {
                    logger.warn("Unexpected permanent failure")
                }
            }
            client.addConnectionListener(reConnectListener)

            logger.info("Kicking user out")
            serverControl.closeConnectionsForUser(rpcTestUser.username)

            assertTrue("Failed to re-connect. " + threadDumpAsString()) { latch.await(60, TimeUnit.SECONDS) }

            client.removeConnectionListener(reConnectListener)

            eventually(duration = 30.seconds) {
                val result = proxy.stringTestMethod()
                assertEquals(StringRPCOpsImpl.testPhrase, result)
            }
            logger.info("Ensured re-connected back")
        }
    }

    @Test(timeout = 300_000)
    fun `multi-threaded scenario`() {
        rpcDriver {
            val server = startRpcServer(listOps = listOf(StringRPCOpsImpl)).get()

            val permanentListener = mock<RPCConnectionListener<StringRPCOps>>()

            val repsCount = 100
            val temporaryListeners = (1..repsCount).map { mock<RPCConnectionListener<StringRPCOps>>() }

            // Establish connection and exchange some calls
            // NB: Connection setup with retry
            val (rpcConnection, client) = startRpcClient(StringRPCOps::class.java,
                    (1..2).map { server.broker.hostAndPort!! },
                    listeners = listOf(permanentListener)).get()
            verify(permanentListener, times(1)).onConnect(any())
            val proxy = rpcConnection.proxy
            assertEquals(StringRPCOpsImpl.testPhrase, proxy.stringTestMethod())

            var addUserIndex = 0
            val addListener = Runnable {
                repeat(repsCount) {
                    logger.debug { "Adding listener #$addUserIndex" }
                    client.addConnectionListener(temporaryListeners[addUserIndex++ % temporaryListeners.size])
                }
            }

            val kickAndReconnectUser = KickAndReconnectCallable(server.broker.serverControl, client, proxy)

            var removerUserIndex = 0
            val removeListener = Runnable {
                repeat(repsCount) {
                    logger.debug { "Removing listener #$removerUserIndex" }
                    client.removeConnectionListener(temporaryListeners[removerUserIndex++ % temporaryListeners.size])
                    Thread.sleep(10)
                }
            }

            val scheduledExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
            listOf(addListener, removeListener).map { scheduledExecutor.scheduleAtFixedRate(it, 100, 100, TimeUnit.MILLISECONDS) }

            val kickAndReconnectExecutor = Executors.newSingleThreadExecutor()

            val kickUserSubmissions = (1..repsCount).map {
                kickAndReconnectExecutor.submit(kickAndReconnectUser)
            }

            kickUserSubmissions.forEach {
                try {
                    it.get(60, TimeUnit.SECONDS)
                } catch (ex : TimeoutException) {
                    logger.warn("Timed out waiting for Future completion. " + threadDumpAsString())
                    throw ex
                }
            }

            scheduledExecutor.shutdown()
            kickAndReconnectExecutor.shutdown()

            verify(permanentListener, never()).onPermanentFailure(any())
            verify(permanentListener, times(repsCount)).onDisconnect(any())
            verify(permanentListener, times(repsCount + 1)).onConnect(any())

            temporaryListeners.forEach { tmpListener ->
                verify(tmpListener, never()).onPermanentFailure(any())
                verify(tmpListener, atLeastOnce()).onDisconnect(any())
                verify(tmpListener, atLeastOnce()).onConnect(any())
            }
        }
    }
}