package com.r3corda.client

import com.r3corda.client.impl.CordaRPCClientImpl
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.services.messaging.*
import com.r3corda.node.utilities.AffinityExecutor
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
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
    lateinit var proxy: ITestOps

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
        serverSession.createTemporaryQueue(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE, ArtemisMessagingComponent.RPC_REQUESTS_QUEUE)
        producer = serverSession.createProducer()
        val dispatcher = object : RPCDispatcher(TestOps()) {
            override fun send(bits: SerializedBytes<*>, toAddress: String) {
                val msg = serverSession.createMessage(false)
                msg.writeBodyBufferBytes(bits.bits)
                producer.send(toAddress, msg)
            }
        }
        serverThread = AffinityExecutor.ServiceAffinityExecutor("unit-tests-rpc-dispatch-thread", 1)
        val serverConsumer = serverSession.createConsumer(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE)
        serverSession.createTemporaryQueue("activemq.notifications", "rpc.qremovals", "_AMQ_NotifType = 'BINDING_REMOVED'")
        val serverNotifConsumer = serverSession.createConsumer("rpc.qremovals")
        dispatcher.start(serverConsumer, serverNotifConsumer, serverThread)

        clientSession = sessionFactory.createSession()
        clientSession.start()

        LogHelper.setLevel("+com.r3corda.rpc"/*, "+org.apache.activemq"*/)

        proxy = CordaRPCClientImpl(clientSession, ReentrantLock(), "tests").proxyFor(ITestOps::class.java)
    }

    @After
    fun shutdown() {
        (proxy as Closeable).close()
        clientSession.stop()
        serverSession.stop()
        artemis.stop()
        serverThread.shutdownNow()
    }

    interface ITestOps : RPCOps {
        @Throws(IllegalArgumentException::class)
        fun barf()

        fun void()

        fun someCalculation(str: String, num: Int): String

        @RPCReturnsObservables
        fun makeObservable(): Observable<Int>

        @RPCReturnsObservables
        fun makeComplicatedObservable(): Observable<Pair<String, Observable<String>>>

        @RPCSinceVersion(2)
        fun addedLater()
    }

    lateinit var complicatedObservable: Observable<Pair<String, Observable<String>>>

    inner class TestOps : ITestOps {
        override val protocolVersion = 1

        override fun barf() {
            throw IllegalArgumentException("Barf!")
        }

        override fun void() { }

        override fun someCalculation(str: String, num: Int) = "$str $num"

        override fun makeObservable(): Observable<Int> {
            return Observable.just(1, 2, 3, 4)
        }

        override fun makeComplicatedObservable() = complicatedObservable

        override fun addedLater() {
            throw UnsupportedOperationException("not implemented")
        }
    }

    @Test
    fun simpleRPCs() {
        // Does nothing, doesn't throw.
        proxy.void()

        assertEquals("Barf!", assertFailsWith<IllegalArgumentException> {
            proxy.barf()
        }.message)

        assertEquals("hi 5", proxy.someCalculation("hi", 5))
    }

    @Test
    fun simpleObservable() {
        // This tests that the observations are transmitted correctly, also completion is transmitted.
        val observations = proxy.makeObservable().toBlocking().toIterable().toList()
        assertEquals(listOf(1, 2, 3, 4), observations)
    }

    @Test
    fun complexObservables() {
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

        assertEquals(1, clientSession.addressQuery(SimpleString("tests.rpc.observations.#")).queueNames.size)

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
        assertEquals(0, clientSession.addressQuery(SimpleString("tests.rpc.observations.#")).queueNames.size)
    }

    @Test
    fun versioning() {
        assertFailsWith<UnsupportedOperationException> { proxy.addedLater() }
    }
}