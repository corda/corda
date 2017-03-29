package net.corda.client.rpc

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.client.rpc.internal.RPCClientConfiguration
import net.corda.core.future
import net.corda.core.messaging.RPCOps
import net.corda.core.random63BitValue
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.loggerFor
import net.corda.node.driver.poll
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.nodeapi.RPCApi
import net.corda.testing.RPCDriverExposedDSLInterface
import net.corda.testing.rpcDriver
import net.corda.testing.startRandomRpcClient
import net.corda.testing.startRpcClient
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(Parameterized::class)
class RPCConcurrencyTests : AbstractRPCTest() {

    /**
     * Holds a "rose"-tree of [Observable]s which allows us to test arbitrary [Observable] nesting in RPC replies.
     */
    @CordaSerializable
    data class ObservableRose<out A>(val value: A, val branches: Observable<out ObservableRose<A>>)

    private interface TestOps : RPCOps {
        fun newLatch(numberOfDowns: Int): Long
        fun waitLatch(id: Long)
        fun downLatch(id: Long)
        fun getImmediateObservableTree(depth: Int, branchingFactor: Int): ObservableRose<Int>
        fun getParallelObservableTree(depth: Int, branchingFactor: Int): ObservableRose<Int>
    }

    class TestOpsImpl : TestOps {
        private val latches = ConcurrentHashMap<Long, CountDownLatch>()
        override val protocolVersion = 0

        override fun newLatch(numberOfDowns: Int): Long {
            val id = random63BitValue()
            val latch = CountDownLatch(numberOfDowns)
            latches.put(id, latch)
            return id
        }

        override fun waitLatch(id: Long) {
            latches[id]!!.await()
        }

        override fun downLatch(id: Long) {
            latches[id]!!.countDown()
        }

        override fun getImmediateObservableTree(depth: Int, branchingFactor: Int): ObservableRose<Int> {
            val branches = if (depth == 0) {
                Observable.empty<ObservableRose<Int>>()
            } else {
                Observable.just(getImmediateObservableTree(depth - 1, branchingFactor)).repeat(branchingFactor.toLong())
            }
            return ObservableRose(depth, branches)
        }

        override fun getParallelObservableTree(depth: Int, branchingFactor: Int): ObservableRose<Int> {
            val branches = if (depth == 0) {
                Observable.empty<ObservableRose<Int>>()
            } else {
                val publish = UnicastSubject.create<ObservableRose<Int>>()
                future {
                    (1..branchingFactor).toList().parallelStream().forEach {
                        publish.onNext(getParallelObservableTree(depth - 1, branchingFactor))
                    }
                    publish.onCompleted()
                }
                publish
            }
            return ObservableRose(depth, branches)
        }
    }

    private lateinit var testOpsImpl: TestOpsImpl
    private fun RPCDriverExposedDSLInterface.testProxy(): TestProxy<TestOps> {
        testOpsImpl = TestOpsImpl()
        return testProxy<TestOps>(
                testOpsImpl,
                clientConfiguration = RPCClientConfiguration.default.copy(
                        reapIntervalMs = 100,
                        cacheConcurrencyLevel = 16
                ),
                serverConfiguration = RPCServerConfiguration.default.copy(
                        rpcThreadPoolSize = 4
                )
        )
    }

    @Test
    fun `call multiple RPCs in parallel`() {
        rpcDriver {
            val proxy = testProxy()
            val numberOfBlockedCalls = 2
            val numberOfDownsRequired = 100
            val id = proxy.ops.newLatch(numberOfDownsRequired)
            val done = CountDownLatch(numberOfBlockedCalls)
            // Start a couple of blocking RPC calls
            (1..numberOfBlockedCalls).forEach {
                future {
                    proxy.ops.waitLatch(id)
                    done.countDown()
                }
            }
            // Down the latch that the others are waiting for concurrently
            (1..numberOfDownsRequired).toList().parallelStream().forEach {
                proxy.ops.downLatch(id)
            }
            done.await()
        }
    }

    private fun intPower(base: Int, power: Int): Int {
        return when (power) {
            0 -> 1
            1 -> base
            else -> {
                val a = intPower(base, power / 2)
                if (power and 1 == 0) {
                    a * a
                } else {
                    a * a * base
                }
            }
        }
    }

    @Test
    fun `nested immediate observables sequence correctly`() {
        rpcDriver {
            // We construct a rose tree of immediate Observables and check that parent observations arrive before children.
            val proxy = testProxy()
            val treeDepth = 6
            val treeBranchingFactor = 3
            val remainingLatch = CountDownLatch((intPower(treeBranchingFactor, treeDepth + 1) - 1) / (treeBranchingFactor - 1))
            val depthsSeen = Collections.synchronizedSet(HashSet<Int>())
            fun ObservableRose<Int>.subscribeToAll() {
                remainingLatch.countDown()
                this.branches.subscribe { tree ->
                    (tree.value + 1..treeDepth - 1).forEach {
                        require(it in depthsSeen) { "Got ${tree.value} before $it" }
                    }
                    depthsSeen.add(tree.value)
                    tree.subscribeToAll()
                }
            }
            proxy.ops.getImmediateObservableTree(treeDepth, treeBranchingFactor).subscribeToAll()
            remainingLatch.await()
        }
    }

    @Test
    fun `parallel nested observables`() {
        rpcDriver {
            val proxy = testProxy()
            val treeDepth = 2
            val treeBranchingFactor = 10
            val remainingLatch = CountDownLatch((intPower(treeBranchingFactor, treeDepth + 1) - 1) / (treeBranchingFactor - 1))
            val depthsSeen = Collections.synchronizedSet(HashSet<Int>())
            fun ObservableRose<Int>.subscribeToAll() {
                remainingLatch.countDown()
                branches.subscribe { tree ->
                    (tree.value + 1..treeDepth - 1).forEach {
                        require(it in depthsSeen) { "Got ${tree.value} before $it" }
                    }
                    depthsSeen.add(tree.value)
                    tree.subscribeToAll()
                }
            }
            proxy.ops.getParallelObservableTree(treeDepth, treeBranchingFactor).subscribeToAll()
            remainingLatch.await()
        }
    }
}