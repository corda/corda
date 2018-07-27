package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.internal.deleteRecursively
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.User
import net.corda.testing.internal.setGlobalSerialization
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.nio.file.Files
import kotlin.test.assertEquals

class RpcWorkerTest {

    private val rpcAddress = NetworkHostAndPort("localhost", 10000)
    private val rpcConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    private val user = User("user1", "test", emptySet())

    private val serializationEnv = setGlobalSerialization(true)

    private val artemisPath = Files.createTempDirectory("RpcWorkerArtemis")
    private val instance = RpcWorker(rpcAddress, user, TestRpcOpsImpl(), artemisPath)

    @Before
    fun setup() {
        instance.start()
    }

    @After
    fun tearDown() {
        instance.close()
        serializationEnv.unset()
        artemisPath.deleteRecursively()
    }

    private fun withConnectionEstablished(block: (rpcOps: TestRpcOps) -> Unit) {
        val client = RPCClient<TestRpcOps>(ArtemisTcpTransport.rpcConnectorTcpTransport(rpcAddress, null), rpcConfiguration)
        val connection = client.start(TestRpcOps::class.java, user.username, user.password)

        try {
            val rpcOps = connection.proxy
            block(rpcOps)
        } finally {
            connection.close()
        }
    }

    @Test
    fun testPing() {
        withConnectionEstablished {rpcOps ->
            assertEquals("pong", rpcOps.ping())
        }
    }

    @Test
    fun testReverse() {
        withConnectionEstablished {rpcOps ->
            val exampleStr = "Makka Pakka"
            assertEquals(exampleStr.reversed(), rpcOps.reverse(exampleStr))
        }
    }

    @Test
    fun testObservable() {
        withConnectionEstablished { rpcOps ->
            val start = 21
            val end = 100
            val observable = rpcOps.incSequence(start)
            observable.take(end - start).zipWith((start..end).asIterable()) { a, b -> Pair(a, b) }.forEach {
                assertEquals(it.first, it.second)
            }
        }
    }

    /**
     * Defines communication protocol
     */
    interface TestRpcOps : RPCOps {

        fun ping() : String

        fun reverse(str : String) : String

        fun incSequence(start : Int) : Observable<Int>
    }

    /**
     * Server side implementation
     */
    class TestRpcOpsImpl : TestRpcOps {

        override val protocolVersion: Int = 1

        override fun ping(): String {
            return "pong"
        }

        override fun reverse(str: String): String {
            return str.reversed()
        }

        override fun incSequence(start: Int): Observable<Int> {
            return Observable.range(start, 100)
        }
    }
}