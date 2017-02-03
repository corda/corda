package net.corda.node.messaging

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.getOrThrow
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.RPCReturnsObservables
import net.corda.core.serialization.SerializedBytes
import net.corda.core.success
import net.corda.core.utilities.LogHelper
import net.corda.node.services.RPCUserService
import net.corda.node.services.User
import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.RPC_REQUESTS_QUEUE
import net.corda.node.services.messaging.CURRENT_RPC_USER
import net.corda.node.services.messaging.CordaRPCClientImpl
import net.corda.node.services.messaging.RPCDispatcher
import net.corda.node.services.messaging.RPCSinceVersion
import net.corda.node.utilities.AffinityExecutor
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import java.io.Closeable
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClientRPCInfrastructureTests {
    // TODO: Test that timeouts work

    lateinit var artemis: EmbeddedActiveMQ
    lateinit var serverSession: ClientSession
    lateinit var clientSession: ClientSession
    lateinit var producer: ClientProducer
    lateinit var serverThread: AffinityExecutor.ServiceAffinityExecutor
    lateinit var proxy: TestOps

    private val authenticatedUser = User("test", "password", permissions = setOf())

    @Before
    fun setup() {
        // Set up an in-memory Artemis with an RPC requests queue.
        artemis = EmbeddedActiveMQ()
        artemis.setConfiguration(ConfigurationImpl().apply {
            acceptorConfigurations = setOf(TransportConfiguration(InVMAcceptorFactory::class.java.name))
            isSecurityEnabled = false
            isPersistenceEnabled = false
        })
        artemis.start()
        val serverLocator = ActiveMQClient.createServerLocatorWithoutHA(TransportConfiguration(InVMConnectorFactory::class.java.name))
        val sessionFactory = serverLocator.createSessionFactory()

        serverSession = sessionFactory.createSession()
        serverSession.start()
        serverSession.createTemporaryQueue(RPC_REQUESTS_QUEUE, RPC_REQUESTS_QUEUE)
        producer = serverSession.createProducer()
        val userService = object : RPCUserService {
            override fun getUser(username: String): User? = throw UnsupportedOperationException()
            override val users: List<User> get() = throw UnsupportedOperationException()
        }
        val dispatcher = object : RPCDispatcher(TestOpsImpl(), userService, "SomeName") {
            override fun send(data: SerializedBytes<*>, toAddress: String) {
                val msg = serverSession.createMessage(false).apply {
                    writeBodyBufferBytes(data.bytes)
                    // Use the magic deduplication property built into Artemis as our message identity too
                    putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
                }
                producer.send(toAddress, msg)
            }

            override fun getUser(message: ClientMessage): User = authenticatedUser
        }
        serverThread = AffinityExecutor.ServiceAffinityExecutor("unit-tests-rpc-dispatch-thread", 1)
        val serverConsumer = serverSession.createConsumer(RPC_REQUESTS_QUEUE)
        serverSession.createTemporaryQueue("activemq.notifications", "rpc.qremovals", "_AMQ_NotifType = 'BINDING_REMOVED'")
        val serverNotifConsumer = serverSession.createConsumer("rpc.qremovals")
        dispatcher.start(serverConsumer, serverNotifConsumer, serverThread)

        clientSession = sessionFactory.createSession()
        clientSession.start()

        LogHelper.setLevel("+net.corda.rpc")

        proxy = CordaRPCClientImpl(clientSession, ReentrantLock(), authenticatedUser.username).proxyFor(TestOps::class.java)
    }

    @After
    fun shutdown() {
        (proxy as Closeable?)?.close()
        clientSession.stop()
        serverSession.stop()
        artemis.stop()
        serverThread.shutdownNow()
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
