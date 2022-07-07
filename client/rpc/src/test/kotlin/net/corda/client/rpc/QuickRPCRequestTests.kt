package net.corda.client.rpc

import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.fork
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.millis
import net.corda.coretesting.internal.testThreadFactory
import net.corda.node.services.rpc.RPCServerConfiguration
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcDriver
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class QuickRPCRequestTests : AbstractRPCTest() {

    companion object {
        // indicate when all RPC threads are busy
        val threadBusyLatches = ConcurrentHashMap<Long, CountDownLatch>()

        val numRpcThreads = 4
    }

    interface TestOps : RPCOps {
        fun newLatch(numberOfDowns: Int): Long
        fun waitLatch(id: Long)
        fun downLatch(id: Long)
    }

    class TestOpsImpl : TestOps {
        private val latches = ConcurrentHashMap<Long, CountDownLatch>()
        override val protocolVersion = 1000

        override fun newLatch(numberOfDowns: Int): Long {
            val id = random63BitValue()
            val latch = CountDownLatch(numberOfDowns)
            latches[id] = latch
            return id
        }

        override fun waitLatch(id: Long) {
            threadBusyLatches[id]!!.countDown()
            latches[id]!!.await()
        }

        override fun downLatch(id: Long) {
            latches[id]!!.countDown()
        }
    }

    private fun RPCDriverDSL.testProxy(): TestProxy<TestOps> {
        return testProxy<TestOps>(
                TestOpsImpl(),
                clientConfiguration = CordaRPCClientConfiguration.DEFAULT.copy(
                        reapInterval = 100.millis
                ),
                serverConfiguration = RPCServerConfiguration.DEFAULT.copy(
                        rpcThreadPoolSize = numRpcThreads
                )
        )
    }

    private val pool = Executors.newFixedThreadPool(10, testThreadFactory())
    @After
    fun shutdown() {
        pool.shutdown()
    }

    @Test(timeout=300_000)
    fun `quick RPCs by-pass the standard RPC thread pool`() {
        /*
            1. Set up a node with N RPC threads
            2. Send a call to a blocking RPC on each thread
            3. When all RPC threads are blocked, call a quick RPC
            4. Check the quick RPC returns, whilst the RPC threads are still blocked
         */
        rpcDriver {
            val proxy = testProxy()
            val numberOfDownsRequired = 1
            val id = proxy.ops.newLatch(numberOfDownsRequired)

            val newThreadLatch = CountDownLatch(numRpcThreads)
            threadBusyLatches[id] = newThreadLatch

            // Flood the RPC threads with blocking calls
            for (n in 1..numRpcThreads) {
                pool.fork {
                    proxy.ops.waitLatch(id)
                }
            }
            // wait until all the RPC threads are blocked
            threadBusyLatches[id]!!.await()
            // try a quick RPC - getProtocolVersion() is always quick
            val quickResult = proxy.ops.protocolVersion.toString()

            // the fact that a result is returned is proof enough that the quick-RPC has by-passed the
            // flooded RPC thread pool
            assertTrue(quickResult.isNotEmpty())

            // The failure condition is that the test times out.
        }
    }
}