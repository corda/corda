package net.corda.client.rpc

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.getOrThrow
import net.corda.core.messaging.RPCOps
import net.corda.core.success
import net.corda.node.services.messaging.getRpcContext
import net.corda.nodeapi.RPCSinceVersion
import net.corda.testing.RPCDriverExposedDSLInterface
import net.corda.testing.rpcDriver
import net.corda.testing.rpcTestUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class ClientRPCInfrastructureTests : AbstractRPCTest() {
    // TODO: Test that timeouts work

    private fun RPCDriverExposedDSLInterface.testProxy() = testProxy<TestOps>(TestOpsImpl()).ops

    interface TestOps : RPCOps {
        @Throws(IllegalArgumentException::class)
        fun barf()

        fun void()

        fun someCalculation(str: String, num: Int): String

        fun makeObservable(): Observable<Int>

        fun makeComplicatedObservable(): Observable<Pair<String, Observable<String>>>

        fun makeListenableFuture(): ListenableFuture<Int>

        fun makeComplicatedListenableFuture(): ListenableFuture<Pair<String, ListenableFuture<String>>>

        @RPCSinceVersion(2)
        fun addedLater()

        fun captureUser(): String
    }

    private lateinit var complicatedObservable: Observable<Pair<String, Observable<String>>>
    private lateinit var complicatedListenableFuturee: ListenableFuture<Pair<String, ListenableFuture<String>>>

    inner class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun barf(): Unit = throw IllegalArgumentException("Barf!")
        override fun void() {}
        override fun someCalculation(str: String, num: Int) = "$str $num"
        override fun makeObservable(): Observable<Int> = Observable.just(1, 2, 3, 4)
        override fun makeListenableFuture(): ListenableFuture<Int> = Futures.immediateFuture(1)
        override fun makeComplicatedObservable() = complicatedObservable
        override fun makeComplicatedListenableFuture(): ListenableFuture<Pair<String, ListenableFuture<String>>> = complicatedListenableFuturee
        override fun addedLater(): Unit = throw IllegalStateException()
        override fun captureUser(): String = getRpcContext().currentUser.username
    }

    @Test
    fun `simple RPCs`() {
        rpcDriver {
            val proxy = testProxy()
            // Does nothing, doesn't throw.
            proxy.void()

            assertEquals("Barf!", assertFailsWith<IllegalArgumentException> {
                proxy.barf()
            }.message)

            assertEquals("hi 5", proxy.someCalculation("hi", 5))
        }
    }

    @Test
    fun `simple observable`() {
        rpcDriver {
            val proxy = testProxy()
            // This tests that the observations are transmitted correctly, also completion is transmitted.
            val observations = proxy.makeObservable().toBlocking().toIterable().toList()
            assertEquals(listOf(1, 2, 3, 4), observations)
        }
    }

    @Test
    fun `complex observables`() {
        rpcDriver {
            val proxy = testProxy()
            // This checks that we can return an object graph with complex usage of observables, like an observable
            // that emits objects that contain more observables.
            val serverQuotes = PublishSubject.create<Pair<String, Observable<String>>>()
            val unsubscribeLatch = CountDownLatch(1)
            complicatedObservable = serverQuotes.asObservable().doOnUnsubscribe { unsubscribeLatch.countDown() }

            val twainQuotes = "Mark Twain" to Observable.just(
                    "I have never let my schooling interfere with my education.",
                    "Clothes make the man. Naked people have little or no influence on society."
            )
            val wildeQuotes = "Oscar Wilde" to Observable.just(
                    "I can resist everything except temptation.",
                    "Always forgive your enemies - nothing annoys them so much."
            )

            val clientQuotes = LinkedBlockingQueue<String>()
            val clientObs = proxy.makeComplicatedObservable()

            val subscription = clientObs.subscribe {
                val name = it.first
                it.second.subscribe {
                    clientQuotes += "Quote by $name: $it"
                }
            }

            assertThat(clientQuotes).isEmpty()

            serverQuotes.onNext(twainQuotes)
            assertEquals("Quote by Mark Twain: I have never let my schooling interfere with my education.", clientQuotes.take())
            assertEquals("Quote by Mark Twain: Clothes make the man. Naked people have little or no influence on society.", clientQuotes.take())

            serverQuotes.onNext(wildeQuotes)
            assertEquals("Quote by Oscar Wilde: I can resist everything except temptation.", clientQuotes.take())
            assertEquals("Quote by Oscar Wilde: Always forgive your enemies - nothing annoys them so much.", clientQuotes.take())

            assertTrue(serverQuotes.hasObservers())
            subscription.unsubscribe()
            unsubscribeLatch.await()
        }
    }

    @Test
    fun `simple ListenableFuture`() {
        rpcDriver {
            val proxy = testProxy()
            val value = proxy.makeListenableFuture().getOrThrow()
            assertThat(value).isEqualTo(1)
        }
    }

    @Test
    fun `complex ListenableFuture`() {
        rpcDriver {
            val proxy = testProxy()
            val serverQuote = SettableFuture.create<Pair<String, ListenableFuture<String>>>()
            complicatedListenableFuturee = serverQuote

            val twainQuote = "Mark Twain" to Futures.immediateFuture("I have never let my schooling interfere with my education.")

            val clientQuotes = LinkedBlockingQueue<String>()
            val clientFuture = proxy.makeComplicatedListenableFuture()

            clientFuture.success {
                val name = it.first
                it.second.success {
                    clientQuotes += "Quote by $name: $it"
                }
            }

            assertThat(clientQuotes).isEmpty()

            serverQuote.set(twainQuote)
            assertThat(clientQuotes.take()).isEqualTo("Quote by Mark Twain: I have never let my schooling interfere with my education.")

            // TODO This final assert sometimes fails because the relevant queue hasn't been removed yet
        }
    }

    @Test
    fun versioning() {
        rpcDriver {
            val proxy = testProxy()
            assertFailsWith<UnsupportedOperationException> { proxy.addedLater() }
        }
    }

    @Test
    fun `authenticated user is available to RPC`() {
        rpcDriver {
            val proxy = testProxy()
            assertThat(proxy.captureUser()).isEqualTo(rpcTestUser.username)
        }
    }
}
