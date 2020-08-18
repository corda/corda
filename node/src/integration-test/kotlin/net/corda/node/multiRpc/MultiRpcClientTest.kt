package net.corda.node.multiRpc

import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.client.rpc.ConnectionFailureException
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

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