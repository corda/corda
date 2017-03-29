package net.corda.client.rpc

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.google.common.util.concurrent.Futures
import net.corda.core.messaging.RPCOps
import net.corda.core.millis
import net.corda.core.random63BitValue
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.RPCKryo
import net.corda.testing.*
import org.apache.activemq.artemis.api.core.SimpleString
import org.bouncycastle.crypto.tls.ConnectionEnd.server
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


class RPCStabilityTests {

    interface LeakObservableOps: RPCOps {
        fun leakObservable(): Observable<Nothing>
    }

    @Test
    fun `client cleans up leaked observables`() {
        rpcDriver {
            val leakObservableOpsImpl = object : LeakObservableOps {
                val leakedUnsubscribedCount = AtomicInteger(0)
                override val protocolVersion = 0
                override fun leakObservable(): Observable<Nothing> {
                    return PublishSubject.create<Nothing>().doOnUnsubscribe {
                        leakedUnsubscribedCount.incrementAndGet()
                    }
                }
            }
            val server = startRpcServer<LeakObservableOps>(ops = leakObservableOpsImpl)
            val proxy = startRpcClient<LeakObservableOps>(server.get().hostAndPort).get()
            // Leak many observables
            val N = 200
            (1..N).toList().parallelStream().forEach {
                proxy.leakObservable()
            }
            // In a loop force GC and check whether the server is notified
            while (true) {
                System.gc()
                if (leakObservableOpsImpl.leakedUnsubscribedCount.get() == N) break
                Thread.sleep(100)
            }
        }
    }

    interface TrackSubscriberOps : RPCOps {
        fun subscribe(): Observable<Unit>
    }

    /**
     * In this test we create a number of out of process RPC clients that call [TrackSubscriberOps.subscribe] in a loop.
     */
    @Test
    fun `server cleans up queues after disconnected clients`() {
        rpcDriver {
            val trackSubscriberOpsImpl = object : TrackSubscriberOps {
                override val protocolVersion = 0
                val subscriberCount = AtomicInteger(0)
                val trackSubscriberCountObservable = UnicastSubject.create<Unit>().share().
                        doOnSubscribe { subscriberCount.incrementAndGet() }.
                        doOnUnsubscribe { subscriberCount.decrementAndGet() }
                override fun subscribe(): Observable<Unit> {
                    return trackSubscriberCountObservable
                }
            }
            val server = startRpcServer<TrackSubscriberOps>(
                    configuration = RPCServerConfiguration.default.copy(
                            reapIntervalMs = 100
                    ),
                    ops = trackSubscriberOpsImpl
            ).get()

            val numberOfClients = 4
            val clients = Futures.allAsList((1 .. numberOfClients).map {
                startRandomRpcClient<TrackSubscriberOps>(server.hostAndPort)
            }).get()

            // Poll until all clients connect
            pollUntilClientNumber(server, numberOfClients)
            pollUntilTrue("number of times subscribe() has been called") { trackSubscriberOpsImpl.subscriberCount.get() >= 100 }.get()
            // Kill one client
            clients[0].destroyForcibly()
            pollUntilClientNumber(server, numberOfClients - 1)
            // Kill the rest
            (1 .. numberOfClients - 1).forEach {
                clients[it].destroyForcibly()
            }
            pollUntilClientNumber(server, 0)
            // Now poll until the server detects the disconnects and unsubscribes from all obserables.
            pollUntilTrue("number of times subscribe() has been called") { trackSubscriberOpsImpl.subscriberCount.get() == 0 }.get()
        }
    }

    interface SlowConsumerRPCOps : RPCOps {
        fun streamAtInterval(interval: Duration, size: Int): Observable<ByteArray>
    }
    class SlowConsumerRPCOpsImpl : SlowConsumerRPCOps {
        override val protocolVersion = 0

        override fun streamAtInterval(interval: Duration, size: Int): Observable<ByteArray> {
            val chunk = ByteArray(size)
            return Observable.interval(interval.toMillis(), TimeUnit.MILLISECONDS).map { chunk }
        }
    }
    val dummyObservableSerialiser = object : Serializer<Observable<Any>>() {
        override fun write(kryo: Kryo?, output: Output?, `object`: Observable<Any>?) {
        }
        override fun read(kryo: Kryo?, input: Input?, type: Class<Observable<Any>>?): Observable<Any> {
            return Observable.empty()
        }
    }
    @Test
    fun `slow consumers are kicked`() {
        val kryoPool = KryoPool.Builder { RPCKryo(dummyObservableSerialiser) }.build()
        rpcDriver {
            val server = startRpcServer(maxBufferedBytesPerClient = 10 * 1024 * 1024, ops = SlowConsumerRPCOpsImpl()).get()

            // Construct an RPC session manually so that we can hang in the message handler
            val myQueue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.test.${random63BitValue()}"
            val session = startArtemisSession(server.hostAndPort)
            session.createTemporaryQueue(myQueue, myQueue)
            val consumer = session.createConsumer(myQueue, null, -1, -1, false)
            consumer.setMessageHandler {
                Thread.sleep(50) // 5x slower than the server producer
                it.acknowledge()
            }
            val producer = session.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME)
            session.start()

            pollUntilClientNumber(server, 1)

            val message = session.createMessage(false)
            val request = RPCApi.ClientToServer.RpcRequest(
                    clientAddress = SimpleString(myQueue),
                    id = RPCApi.RpcRequestId(random63BitValue()),
                    methodName = SlowConsumerRPCOps::streamAtInterval.name,
                    arguments = listOf(10.millis, 123456)
            )
            request.writeToClientMessage(kryoPool, message)
            producer.send(message)
            session.commit()

            // We are consuming slower than the server is producing, so we should be kicked after a while
            pollUntilClientNumber(server, 0)
        }
    }

}

fun RPCDriverExposedDSLInterface.pollUntilClientNumber(server: RpcServerHandle, expected: Int) {
    pollUntilTrue("number of RPC clients to become $expected") {
        val clientAddresses = server.serverControl.addressNames.filter { it.startsWith(RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX) }
        clientAddresses.size == expected
    }.get()
}