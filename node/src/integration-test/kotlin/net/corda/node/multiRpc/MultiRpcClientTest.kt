package net.corda.node.multiRpc

import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import net.corda.client.rpc.ConnectionFailureException
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.ext.MultiRPCClient
import net.corda.client.rpc.ext.RPCConnectionListener
import net.corda.core.internal.messaging.AttachmentTrustInfoRPCOps
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._rpcClientSerializationEnv
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.common.internal.eventually
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.use

class MultiRpcClientTest {

    companion object {
        private fun ensureSerialisationEnvNull() {
            // Ensure that RPC client serialisation environment is definitely not set
            if (_rpcClientSerializationEnv.get() != null) {
                _rpcClientSerializationEnv.set(null)
            }
        }
    }

    private var prevRpcClientSerializationEnv: SerializationEnvironment? = null

    @Before
    fun setup() {
        prevRpcClientSerializationEnv = _rpcClientSerializationEnv.get()
        ensureSerialisationEnvNull()
    }

    @After
    fun after() {
        ensureSerialisationEnvNull()
        // Restore something that was changed during setup
        prevRpcClientSerializationEnv?.let { _rpcClientSerializationEnv.set(prevRpcClientSerializationEnv) }
    }

    @Test(timeout = 300_000)
    fun `can connect to custom RPC interface`() {

        // Allocate named port to be used for RPC interaction
        val rpcAddress = incrementalPortAllocation().nextHostAndPort()

        // Create a specific RPC user
        val rpcUser = User("MultiRpcClientTest", "MultiRpcClientTestPwd", setOf(all()))

        // Create client with RPC address specified
        val client = MultiRPCClient(rpcAddress, AttachmentTrustInfoRPCOps::class.java, rpcUser.username, rpcUser.password)

        // Ensure that RPC client definitely sets serialisation environment
        assertNotNull(_rpcClientSerializationEnv.get())

        // Right from the start attach a listener such that it will be informed of all the activity happening for this RPC client
        val listener = mock<RPCConnectionListener<AttachmentTrustInfoRPCOps>>()
        client.addConnectionListener(listener)

        client.use {
            // Starting node out-of-process to ensure it is completely independent from RPC client
            driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
                startNode(providedName = ALICE_NAME,
                        defaultParameters = NodeParameters(rpcAddress = rpcAddress, rpcUsers = listOf(rpcUser))).getOrThrow()

                val connFuture = client.start()
                eventually(duration = 60.seconds) {
                    verify(listener, times(1)).onConnect(argThat { connectionOpt === connFuture.get() })
                }

                val conn = connFuture.get()
                conn.use {
                    assertNotNull(it.proxy.attachmentTrustInfos)
                }
                verify(listener, times(1)).onDisconnect(argThat { connectionOpt === conn && throwableOpt == null })
                // Ensuring that calling start even after close will result in the same future
                assertSame(connFuture, client.start())
            }
        }
    }

    @Test(timeout = 300_000)
    fun `client with useGlobalThreadPools set to true should start global thread pools`() {
        // Allocate named port to be used for RPC interaction
        val rpcAddress = incrementalPortAllocation().nextHostAndPort()
        val rpcUser = User("MultiRpcClient1", "MultiRpcClient1Pwd", setOf(all()))

        val globalClient = MultiRPCClient(
                rpcAddress,
                AttachmentTrustInfoRPCOps::class.java,
                rpcUser.username,
                rpcUser.password,
                customSerializers = null,
                configuration = CordaRPCClientConfiguration.DEFAULT,
                sslConfiguration = null,
                classLoader = null,
                externalTrace = null,
                impersonatedActor = null,
                targetLegalIdentity = null,
                useGlobalThreadPools = true
        )

        // Right from the start attach a listener such that it will be informed of all the activity happening for this RPC client
        val globalListener = mock<RPCConnectionListener<AttachmentTrustInfoRPCOps>>()
        globalClient.addConnectionListener(globalListener)

        globalClient.use {
            // Starting node out-of-process to ensure it is completely independent from RPC client
            driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
                startNode(providedName = ALICE_NAME,
                        defaultParameters = NodeParameters(rpcAddress = rpcAddress, rpcUsers = listOf(rpcUser))).getOrThrow()

                val connFuture = globalClient.start()
                eventually(duration = 60.seconds) {
                    verify(globalListener, times(1)).onConnect(argThat { connectionOpt === connFuture.get() })
                }
            }
        }

        val globalPool = ActiveMQClient.getGlobalThreadPool() as? ThreadPoolExecutor
                ?: error("Expected global thread pool to be a ThreadPoolExecutor")
        val schedPool = ActiveMQClient.getGlobalScheduledThreadPool() as? ScheduledThreadPoolExecutor
                ?: error("Expected global scheduled pool to be a ScheduledThreadPoolExecutor")

        assertTrue(globalPool.poolSize > 0, "Global thread pool should be initialised and should have non-zero pool size")
        assertTrue(globalPool.completedTaskCount > 0, "Global thread pool should be initialised and should have some completed tasks")
        assertTrue(schedPool.poolSize > 0, "Scheduled thread pool should be initialised and should have non-zero pool size")
        assertTrue(schedPool.completedTaskCount > 0, "Scheduled thread pool should be initialised and should have some completed tasks")
    }

    @Test(timeout = 300_000)
    fun `client with useGlobalThreadPools set to false should not start global thread pools`() {
        // Allocate named port to be used for RPC interaction
        val rpcAddress = incrementalPortAllocation().nextHostAndPort()
        val rpcUser = User("MultiRpcClient1", "MultiRpcClient1Pwd", setOf(all()))

        val localClient = MultiRPCClient(
                rpcAddress,
                AttachmentTrustInfoRPCOps::class.java,
                rpcUser.username,
                rpcUser.password,
                customSerializers = null,
                configuration = CordaRPCClientConfiguration.DEFAULT,
                sslConfiguration = null,
                classLoader = null,
                externalTrace = null,
                impersonatedActor = null,
                targetLegalIdentity = null,
                useGlobalThreadPools = false
        )

        // Right from the start attach a listener such that it will be informed of all the activity happening for this RPC client
        val localListener = mock<RPCConnectionListener<AttachmentTrustInfoRPCOps>>()
        localClient.addConnectionListener(localListener)

        localClient.use {
            // Starting node out-of-process to ensure it is completely independent from RPC client
            driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
                startNode(providedName = ALICE_NAME,
                        defaultParameters = NodeParameters(rpcAddress = rpcAddress, rpcUsers = listOf(rpcUser))).getOrThrow()

                val connFuture = localClient.start()
                eventually(duration = 60.seconds) {
                    verify(localListener, times(1)).onConnect(argThat { connectionOpt === connFuture.get() })
                }
            }
        }

        val globalPool = ActiveMQClient.getGlobalThreadPool() as? ThreadPoolExecutor
                ?: error("Expected global thread pool to be a ThreadPoolExecutor")
        val schedPool = ActiveMQClient.getGlobalScheduledThreadPool() as? ScheduledThreadPoolExecutor
                ?: error("Expected global scheduled pool to be a ScheduledThreadPoolExecutor")

        assertEquals(0, globalPool.poolSize, "Global thread pool should not be initialised and should have zero pool size")
        assertEquals(0, globalPool.completedTaskCount, "Global thread pool should not be initialised and should have zero completed tasks")
        assertEquals(0, schedPool.poolSize, "Scheduled thread pool should not be initialised and should have zero pool size")
        assertEquals(0, schedPool.completedTaskCount, "Scheduled thread pool should not be initialised and should have zero completed tasks")
    }

    @Test(timeout = 300_000)
    fun `ensure onError populated on disconnect`() {

        // Allocate named port to be used for RPC interaction
        val rpcAddress = incrementalPortAllocation().nextHostAndPort()

        // Create a specific RPC user
        val rpcUser = User("MultiRpcClientTest2", "MultiRpcClientTestPwd2", setOf(all()))

        // Create client with RPC address specified
        val client = MultiRPCClient(rpcAddress, CordaRPCOps::class.java, rpcUser.username, rpcUser.password)

        val observer = mock<Observer<NetworkMapCache.MapChange>>()

        client.use {
            driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
                startNode(providedName = ALICE_NAME,
                        defaultParameters = NodeParameters(rpcAddress = rpcAddress, rpcUsers = listOf(rpcUser))).getOrThrow()

                val connFuture = client.start()
                val conn = connFuture.get()
                val nmFeed = conn.proxy.networkMapFeed()
                assertEquals(ALICE_NAME, nmFeed.snapshot.single().legalIdentities.single().name)
                nmFeed.updates.subscribe(observer)
            }
        }

        eventually {
            verify(observer, times(1)).onError(argThat { this as? ConnectionFailureException != null })
        }
    }
}
