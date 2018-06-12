package net.corda.client.rpc

import net.corda.client.rpc.internal.CordaRPCClientConfigurationImpl
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcDriver
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import rx.Observable
import rx.subjects.UnicastSubject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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

    class TestOpsImpl(private val pool: Executor) : TestOps {
        private val latches = ConcurrentHashMap<Long, CountDownLatch>()
        override val protocolVersion = 0

        override fun newLatch(numberOfDowns: Int): Long {
            val id = random63BitValue()
            val latch = CountDownLatch(numberOfDowns)
            latches[id] = latch
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
                UnicastSubject.create<ObservableRose<Int>>().also { publish ->
                    (1..branchingFactor).map {
                        pool.fork { publish.onNext(getParallelObservableTree(depth - 1, branchingFactor)) }
                    }.transpose().then {
                        it.getOrThrow()
                        publish.onCompleted()
                    }
                }
            }
            return ObservableRose(depth, branches)
        }
    }

    private fun RPCDriverDSL.testProxy(): TestProxy<TestOps> {
        return testProxy<TestOps>(
                TestOpsImpl(pool),
                clientConfiguration = CordaRPCClientConfigurationImpl.DEFAULT.copy(
                        reapInterval = 100.millis
                ),
                serverConfiguration = RPCServerConfiguration.default.copy(
                        rpcThreadPoolSize = 4
                )
        )
    }

    private val pool = Executors.newFixedThreadPool(10, testThreadFactory())
    @After
    fun shutdown() {
        pool.shutdown()
    }

    @Test
    fun `call multiple RPCs in parallel`() {
        rpcDriver {
            val proxy = testProxy()
            val numberOfBlockedCalls = 2
            val numberOfDownsRequired = 100
            val id = proxy.ops.newLatch(numberOfDownsRequired)
            // Start a couple of blocking RPC calls
            val done = (1..numberOfBlockedCalls).map {
                pool.fork {
                    proxy.ops.waitLatch(id)
                }
            }.transpose()
            // Down the latch that the others are waiting for concurrently
            (1..numberOfDownsRequired).map {
                pool.fork { proxy.ops.downLatch(id) }
            }.transpose().getOrThrow()
            done.getOrThrow()
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
                    (tree.value + 1 until treeDepth).forEach {
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
            val depthsSeen = ConcurrentHashSet<Int>()
            fun ObservableRose<Int>.subscribeToAll() {
                remainingLatch.countDown()
                branches.subscribe { tree ->
                    (tree.value + 1 until treeDepth).forEach {
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