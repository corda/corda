package net.corda.client.rpc

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.getOrThrow
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.RPCReturnsObservables
import net.corda.core.success
import net.corda.nodeapi.CURRENT_RPC_USER
import net.corda.nodeapi.RPCSinceVersion
import net.corda.nodeapi.User
import org.apache.activemq.artemis.api.core.SimpleString
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClientRPCInfrastructureTests : AbstractClientRPCTest() {
    // TODO: Test that timeouts work

    lateinit var proxy: TestOps

    private val authenticatedUser = User("test", "password", permissions = setOf())

    @Before
    fun setup() {
        proxy = rpcProxyFor(authenticatedUser, TestOpsImpl(), TestOps::class.java)
    }

    @After
    fun shutdown() {
        safeClose(proxy)
    }

    interface TestOps : RPCOps {
        @Throws(IllegalArgumentException::class)
        fun barf()

        fun void()

        fun someCalculation(str: String, num: Int): String

        @RPCReturnsObservables
        fun makeObservable(): Observable<Int>

        @RPCReturnsObservables
        fun makeComplicatedObservable(): Observable<Pair<String, Observable<String>>>

        @RPCReturnsObservables
        fun makeListenableFuture(): ListenableFuture<Int>

        @RPCReturnsObservables
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
        override fun addedLater(): Unit = throw UnsupportedOperationException("not implemented")
        override fun captureUser(): String = CURRENT_RPC_USER.get().username
    }

    @Test
    fun `simple RPCs`() {
        // Does nothing, doesn't throw.
        proxy.void()

        assertEquals("Barf!", assertFailsWith<IllegalArgumentException> {
            proxy.barf()
        }.message)

        assertEquals("hi 5", proxy.someCalculation("hi", 5))
    }

    @Test
    fun `simple observable`() {
        // This tests that the observations are transmitted correctly, also completion is transmitted.
        val observations = proxy.makeObservable().toBlocking().toIterable().toList()
        assertEquals(listOf(1, 2, 3, 4), observations)
    }

    @Test
    fun `complex observables`() {
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

        val rpcQueuesQuery = SimpleString("clients.${authenticatedUser.username}.rpc.*")
        assertEquals(2, clientSession.addressQuery(rpcQueuesQuery).queueNames.size)

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
        assertEquals(1, clientSession.addressQuery(rpcQueuesQuery).queueNames.size)
    }

    @Test
    fun `simple ListenableFuture`() {
        val value = proxy.makeListenableFuture().getOrThrow()
        assertThat(value).isEqualTo(1)
    }

    @Test
    fun `complex ListenableFuture`() {
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

        val rpcQueuesQuery = SimpleString("clients.${authenticatedUser.username}.rpc.*")
        assertEquals(2, clientSession.addressQuery(rpcQueuesQuery).queueNames.size)

        assertThat(clientQuotes).isEmpty()

        serverQuote.set(twainQuote)
        assertThat(clientQuotes.take()).isEqualTo("Quote by Mark Twain: I have never let my schooling interfere with my education.")

        // TODO This final assert sometimes fails because the relevant queue hasn't been removed yet
//        assertEquals(1, clientSession.addressQuery(rpcQueuesQuery).queueNames.size)
    }

    @Test
    fun versioning() {
        assertFailsWith<UnsupportedOperationException> { proxy.addedLater() }
    }

    @Test
    fun `authenticated user is available to RPC`() {
        assertThat(proxy.captureUser()).isEqualTo(authenticatedUser.username)
    }
}
