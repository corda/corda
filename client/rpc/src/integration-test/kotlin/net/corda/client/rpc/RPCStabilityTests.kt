//package net.corda.client.rpc
//
//import net.corda.client.rpc.internal.RPCClient
//import net.corda.core.context.Trace
//import net.corda.core.crypto.random63BitValue
//import net.corda.core.internal.concurrent.fork
//import net.corda.core.internal.concurrent.transpose
//import net.corda.core.messaging.RPCOps
//import net.corda.core.serialization.SerializationDefaults
//import net.corda.core.serialization.serialize
//import net.corda.core.utilities.*
//import net.corda.node.services.messaging.RPCServerConfiguration
//import net.corda.nodeapi.RPCApi
//import net.corda.nodeapi.eventually
//import net.corda.testing.core.SerializationEnvironmentRule
//import net.corda.testing.core.freePort
//import net.corda.testing.internal.testThreadFactory
//import net.corda.testing.node.internal.*
//import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
//import org.apache.activemq.artemis.api.core.SimpleString
//import org.junit.After
//import org.junit.Assert.*
//import org.junit.Rule
//import org.junit.Test
//import rx.Observable
//import rx.subjects.PublishSubject
//import rx.subjects.UnicastSubject
//import java.time.Duration
//import java.util.concurrent.ConcurrentLinkedQueue
//import java.util.concurrent.Executors
//import java.util.concurrent.ScheduledExecutorService
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.AtomicInteger
//import java.util.concurrent.atomic.AtomicLong
//
//class RPCStabilityTests {
//    @Rule
//    @JvmField
//    val testSerialization = SerializationEnvironmentRule(true)
//    private val pool = Executors.newFixedThreadPool(10, testThreadFactory())
//    @After
//    fun shutdown() {
//        pool.shutdown()
//    }
//
//    object DummyOps : RPCOps {
//        override val protocolVersion = 0
//    }
//
//    private fun waitUntilNumberOfThreadsStable(executorService: ScheduledExecutorService): Map<Thread, List<StackTraceElement>> {
//        val values = ConcurrentLinkedQueue<Map<Thread, List<StackTraceElement>>>()
//        return poll(executorService, "number of threads to become stable", 250.millis) {
//            values.add(Thread.getAllStackTraces().mapValues { it.value.toList() })
//            if (values.size > 5) {
//                values.poll()
//            }
//            val first = values.peek()
//            if (values.size == 5 && values.all { it.keys.size == first.keys.size }) {
//                first
//            } else {
//                null
//            }
//        }.get()
//    }
//
//    @Test
//    fun `client and server dont leak threads`() {
//        fun startAndStop() {
//            rpcDriver {
//                val server = startRpcServer<RPCOps>(ops = DummyOps).get()
//                startRpcClient<RPCOps>(server.broker.hostAndPort!!).get()
//            }
//        }
//
//        runBlockAndCheckThreads(::startAndStop)
//    }
//
//    private fun runBlockAndCheckThreads(block: () -> Unit) {
//        val executor = Executors.newScheduledThreadPool(1)
//
//        try {
//            // Warm-up so that all the thread pools & co. created
//            block()
//
//            val threadsBefore = waitUntilNumberOfThreadsStable(executor)
//            repeat(5) {
//                block()
//            }
//            val threadsAfter = waitUntilNumberOfThreadsStable(executor)
//            // This is a less than check because threads from other tests may be shutting down while this test is running.
//            // This is therefore a "best effort" check. When this test is run on its own this should be a strict equality.
//            // In case of failure we output the threads along with their stacktraces to get an idea what was running at a time.
//            require(threadsBefore.keys.size >= threadsAfter.keys.size, { "threadsBefore: $threadsBefore\nthreadsAfter: $threadsAfter" })
//        } finally {
//            executor.shutdownNow()
//        }
//    }
//
//    @Test
//    fun `client doesnt leak threads when it fails to start`() {
//        fun startAndStop() {
//            rpcDriver {
//                Try.on { startRpcClient<RPCOps>(NetworkHostAndPort("localhost", 9999)).get() }
//                val server = startRpcServer<RPCOps>(ops = DummyOps)
//                Try.on {
//                    startRpcClient<RPCOps>(
//                            server.get().broker.hostAndPort!!,
//                            configuration = CordaRPCClientConfiguration.DEFAULT.copy(minimumServerProtocolVersion = 1)
//                    ).get()
//                }
//            }
//        }
//        runBlockAndCheckThreads(::startAndStop)
//    }
//
//    private fun RpcBrokerHandle.getStats(): Map<String, Any> {
//        return serverControl.run {
//            mapOf(
//                    "connections" to listConnectionIDs().toSet(),
//                    "sessionCount" to listConnectionIDs().flatMap { listSessions(it).toList() }.size,
//                    "consumerCount" to totalConsumerCount
//            )
//        }
//    }
//
//    @Test
//    fun `rpc server close doesnt leak broker resources`() {
//        rpcDriver {
//            fun startAndCloseServer(broker: RpcBrokerHandle) {
//                startRpcServerWithBrokerRunning(
//                        configuration = RPCServerConfiguration.DEFAULT,
//                        ops = DummyOps,
//                        brokerHandle = broker
//                ).rpcServer.close()
//            }
//
//            val broker = startRpcBroker().get()
//            startAndCloseServer(broker)
//            val initial = broker.getStats()
//            repeat(100) {
//                startAndCloseServer(broker)
//            }
//            pollUntilTrue("broker resources to be released") {
//                initial == broker.getStats()
//            }
//        }
//    }
//
//    @Test
//    fun `rpc client close doesnt leak broker resources`() {
//        rpcDriver {
//            val server = startRpcServer(configuration = RPCServerConfiguration.DEFAULT, ops = DummyOps).get()
//            RPCClient<RPCOps>(server.broker.hostAndPort!!).start(RPCOps::class.java, rpcTestUser.username, rpcTestUser.password).close()
//            val initial = server.broker.getStats()
//            repeat(100) {
//                val connection = RPCClient<RPCOps>(server.broker.hostAndPort!!).start(RPCOps::class.java, rpcTestUser.username, rpcTestUser.password)
//                connection.close()
//            }
//            pollUntilTrue("broker resources to be released") {
//                initial == server.broker.getStats()
//            }
//        }
//    }
//
//    @Test
//    fun `rpc server close is idempotent`() {
//        rpcDriver {
//            val server = startRpcServer(ops = DummyOps).get()
//            repeat(10) {
//                server.rpcServer.close()
//            }
//        }
//    }
//
//    @Test
//    fun `rpc client close is idempotent`() {
//        rpcDriver {
//            val serverShutdown = shutdownManager.follower()
//            val server = startRpcServer(ops = DummyOps).get()
//            serverShutdown.unfollow()
//            // With the server up
//            val connection1 = RPCClient<RPCOps>(server.broker.hostAndPort!!).start(RPCOps::class.java, rpcTestUser.username, rpcTestUser.password)
//            repeat(10) {
//                connection1.close()
//            }
//            val connection2 = RPCClient<RPCOps>(server.broker.hostAndPort!!).start(RPCOps::class.java, rpcTestUser.username, rpcTestUser.password)
//            serverShutdown.shutdown()
//            // With the server down
//            repeat(10) {
//                connection2.close()
//            }
//        }
//    }
//
//    interface LeakObservableOps : RPCOps {
//        fun leakObservable(): Observable<Nothing>
//    }
//
//    @Test
//    fun `client cleans up leaked observables`() {
//        rpcDriver {
//            val leakObservableOpsImpl = object : LeakObservableOps {
//                val leakedUnsubscribedCount = AtomicInteger(0)
//                override val protocolVersion = 0
//                override fun leakObservable(): Observable<Nothing> {
//                    return PublishSubject.create<Nothing>().doOnUnsubscribe {
//                        leakedUnsubscribedCount.incrementAndGet()
//                    }
//                }
//            }
//            val server = startRpcServer<LeakObservableOps>(ops = leakObservableOpsImpl)
//            val proxy = startRpcClient<LeakObservableOps>(server.get().broker.hostAndPort!!).get()
//            // Leak many observables
//            val count = 200
//            (1..count).map {
//                pool.fork { proxy.leakObservable(); Unit }
//            }.transpose().getOrThrow()
//            // In a loop force GC and check whether the server is notified
//            while (true) {
//                System.gc()
//                if (leakObservableOpsImpl.leakedUnsubscribedCount.get() == count) break
//                Thread.sleep(100)
//            }
//        }
//    }
//
//    interface ReconnectOps : RPCOps {
//        fun ping(): String
//    }
//
//    @Test
//    fun `client reconnects to rebooted server`() {
//        rpcDriver {
//            val ops = object : ReconnectOps {
//                override val protocolVersion = 0
//                override fun ping() = "pong"
//            }
//
//            val serverFollower = shutdownManager.follower()
//            val serverPort = startRpcServer<ReconnectOps>(ops = ops).getOrThrow().broker.hostAndPort!!
//            serverFollower.unfollow()
//            // Set retry interval to 1s to reduce test duration
//            val clientConfiguration = CordaRPCClientConfiguration.DEFAULT.copy(connectionRetryInterval = 1.seconds)
//            val clientFollower = shutdownManager.follower()
//            val client = startRpcClient<ReconnectOps>(serverPort, configuration = clientConfiguration).getOrThrow()
//            clientFollower.unfollow()
//            assertEquals("pong", client.ping())
//            serverFollower.shutdown()
//            startRpcServer<ReconnectOps>(ops = ops, customPort = serverPort).getOrThrow()
//            val response = eventually<RPCException, String>(10.seconds) { client.ping() }
//            assertEquals("pong", response)
//            clientFollower.shutdown() // Driver would do this after the new server, causing hang.
//        }
//    }
//
//    @Test
//    fun `connection failover fails, rpc calls throw`() {
//        rpcDriver {
//            val ops = object : ReconnectOps {
//                override val protocolVersion = 0
//                override fun ping() = "pong"
//            }
//
//            val serverFollower = shutdownManager.follower()
//            val serverPort = startRpcServer<ReconnectOps>(ops = ops).getOrThrow().broker.hostAndPort!!
//            serverFollower.unfollow()
//            // Set retry interval to 1s to reduce test duration
//            val clientConfiguration = CordaRPCClientConfiguration.DEFAULT.copy(connectionRetryInterval = 1.seconds, maxReconnectAttempts = 5)
//            val clientFollower = shutdownManager.follower()
//            val client = startRpcClient<ReconnectOps>(serverPort, configuration = clientConfiguration).getOrThrow()
//            clientFollower.unfollow()
//            assertEquals("pong", client.ping())
//            serverFollower.shutdown()
//            try {
//                client.ping()
//            } catch (e: Exception) {
//                assertTrue(e is RPCException)
//            }
//            clientFollower.shutdown() // Driver would do this after the new server, causing hang.
//        }
//    }
//
//    interface NoOps : RPCOps {
//        fun subscribe(): Observable<Nothing>
//    }
//
//    @Test
//    fun `observables error when connection breaks`() {
//        rpcDriver {
//            val ops = object : NoOps {
//                override val protocolVersion = 0
//                override fun subscribe(): Observable<Nothing> {
//                    return PublishSubject.create<Nothing>()
//                }
//            }
//            val serverFollower = shutdownManager.follower()
//            val serverPort = startRpcServer<NoOps>(ops = ops).getOrThrow().broker.hostAndPort!!
//            serverFollower.unfollow()
//
//            val clientConfiguration = CordaRPCClientConfiguration.DEFAULT.copy(connectionRetryInterval = 500.millis, maxReconnectAttempts = 1)
//            val clientFollower = shutdownManager.follower()
//            val client = startRpcClient<NoOps>(serverPort, configuration = clientConfiguration).getOrThrow()
//            clientFollower.unfollow()
//
//            var terminateHandlerCalled = false
//            var errorHandlerCalled = false
//            var exceptionMessage: String? = null
//            val subscription = client.subscribe()
//                     .doOnTerminate{ terminateHandlerCalled = true }
//                     .subscribe({}, {
//                         errorHandlerCalled = true
//                         //log exception
//                         exceptionMessage = it.message
//                     })
//
//            serverFollower.shutdown()
//            Thread.sleep(100)
//
//            assertTrue(terminateHandlerCalled)
//            assertTrue(errorHandlerCalled)
//            assertEquals("Connection failure detected.", exceptionMessage)
//            assertTrue(subscription.isUnsubscribed)
//
//            clientFollower.shutdown() // Driver would do this after the new server, causing hang.
//        }
//    }
//
//    @Test
//    fun `client throws RPCException after initial connection attempt fails`() {
//        val client = CordaRPCClient(NetworkHostAndPort("localhost", freePort()))
//        var exceptionMessage: String? = null
//        try {
//           client.start("user", "pass").proxy
//        } catch (e1: RPCException) {
//            exceptionMessage = e1.message
//        } catch (e2: Exception) {
//            fail("Expected RPCException to be thrown. Received ${e2.javaClass.simpleName} instead.")
//        }
//        assertNotNull(exceptionMessage)
//        assertEquals("Cannot connect to server(s). Tried with all available servers.", exceptionMessage)
//    }
//
//    interface ServerOps : RPCOps {
//        fun serverId(): String
//    }
//
//    @Test
//    fun `client connects to first available server`() {
//        rpcDriver {
//            val ops = object : ServerOps {
//                override val protocolVersion = 0
//                override fun serverId() = "server"
//            }
//            val serverFollower = shutdownManager.follower()
//            val serverAddress = startRpcServer<RPCOps>(ops = ops).getOrThrow().broker.hostAndPort!!
//            serverFollower.unfollow()
//
//            val clientFollower = shutdownManager.follower()
//            val client = startRpcClient<ServerOps>(listOf(NetworkHostAndPort("localhost", 12345), serverAddress, NetworkHostAndPort("localhost", 54321))).getOrThrow()
//            clientFollower.unfollow()
//
//            assertEquals("server", client.serverId())
//
//            clientFollower.shutdown() // Driver would do this after the new server, causing hang.
//        }
//    }
//
//    @Test
//    fun `3 server failover`() {
//        rpcDriver {
//            val ops1 = object : ServerOps {
//                override val protocolVersion = 0
//                override fun serverId() = "server1"
//            }
//            val ops2 = object : ServerOps {
//                override val protocolVersion = 0
//                override fun serverId() = "server2"
//            }
//            val ops3 = object : ServerOps {
//                override val protocolVersion = 0
//                override fun serverId() = "server3"
//            }
//            val serverFollower1 = shutdownManager.follower()
//            val server1 = startRpcServer<RPCOps>(ops = ops1).getOrThrow()
//            serverFollower1.unfollow()
//
//            val serverFollower2 = shutdownManager.follower()
//            val server2 = startRpcServer<RPCOps>(ops = ops2).getOrThrow()
//            serverFollower2.unfollow()
//
//            val serverFollower3 = shutdownManager.follower()
//            val server3 = startRpcServer<RPCOps>(ops = ops3).getOrThrow()
//            serverFollower3.unfollow()
//            val servers = mutableMapOf("server1" to serverFollower1, "server2" to serverFollower2, "server3" to serverFollower3)
//
//            val clientFollower = shutdownManager.follower()
//            val client = startRpcClient<ServerOps>(listOf(server1.broker.hostAndPort!!, server2.broker.hostAndPort!!, server3.broker.hostAndPort!!)).getOrThrow()
//            clientFollower.unfollow()
//
//            var response = client.serverId()
//            assertTrue(servers.containsKey(response))
//            servers[response]!!.shutdown()
//            servers.remove(response)
//
//            // Failover will take some time.
//            while (true) {
//                try {
//                    response = client.serverId()
//                    break
//                } catch (e: RPCException) {}
//            }
//            assertTrue(servers.containsKey(response))
//            servers[response]!!.shutdown()
//            servers.remove(response)
//
//            while (true) {
//                try {
//                    response = client.serverId()
//                    break
//                } catch (e: RPCException) {}
//            }
//            assertTrue(servers.containsKey(response))
//            servers[response]!!.shutdown()
//            servers.remove(response)
//
//            assertTrue(servers.isEmpty())
//
//            clientFollower.shutdown() // Driver would do this after the new server, causing hang.
//
//        }
//    }
//
//    interface TrackSubscriberOps : RPCOps {
//        fun subscribe(): Observable<Unit>
//    }
//
//    /**
//     * In this test we create a number of out of process RPC clients that call [TrackSubscriberOps.subscribe] in a loop.
//     */
//    @Test
//    fun `server cleans up queues after disconnected clients`() {
//        rpcDriver {
//            val trackSubscriberOpsImpl = object : TrackSubscriberOps {
//                override val protocolVersion = 0
//                val subscriberCount = AtomicInteger(0)
//                val trackSubscriberCountObservable = UnicastSubject.create<Unit>().share().
//                        doOnSubscribe { subscriberCount.incrementAndGet() }.
//                        doOnUnsubscribe { subscriberCount.decrementAndGet() }
//
//                override fun subscribe(): Observable<Unit> {
//                    return trackSubscriberCountObservable
//                }
//            }
//            val server = startRpcServer<TrackSubscriberOps>(
//                    configuration = RPCServerConfiguration.DEFAULT.copy(
//                            reapInterval = 100.millis
//                    ),
//                    ops = trackSubscriberOpsImpl
//            ).get()
//
//            val numberOfClients = 4
//            val clients = (1..numberOfClients).map {
//                startRandomRpcClient<TrackSubscriberOps>(server.broker.hostAndPort!!)
//            }.transpose().get()
//
//            // Poll until all clients connect
//            pollUntilClientNumber(server, numberOfClients)
//            pollUntilTrue("number of times subscribe() has been called") { trackSubscriberOpsImpl.subscriberCount.get() >= 100 }.get()
//            // Kill one client
//            clients[0].destroyForcibly()
//            pollUntilClientNumber(server, numberOfClients - 1)
//            // Kill the rest
//            (1 until numberOfClients).forEach {
//                clients[it].destroyForcibly()
//            }
//            pollUntilClientNumber(server, 0)
//            // Now poll until the server detects the disconnects and un-subscribes from all observables.
//            pollUntilTrue("number of times subscribe() has been called") { trackSubscriberOpsImpl.subscriberCount.get() == 0 }.get()
//        }
//    }
//
//    interface SlowConsumerRPCOps : RPCOps {
//        fun streamAtInterval(interval: Duration, size: Int): Observable<ByteArray>
//    }
//
//    class SlowConsumerRPCOpsImpl : SlowConsumerRPCOps {
//        override val protocolVersion = 0
//
//        override fun streamAtInterval(interval: Duration, size: Int): Observable<ByteArray> {
//            val chunk = ByteArray(size)
//            return Observable.interval(interval.toMillis(), TimeUnit.MILLISECONDS).map { chunk }
//        }
//    }
//
//    @Test
//    fun `slow consumers are kicked`() {
//        rpcDriver {
//            val server = startRpcServer(maxBufferedBytesPerClient = 10 * 1024 * 1024, ops = SlowConsumerRPCOpsImpl()).get()
//
//            // Construct an RPC session manually so that we can hang in the message handler
//            val myQueue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.test.${random63BitValue()}"
//            val session = startArtemisSession(server.broker.hostAndPort!!)
//            session.createTemporaryQueue(myQueue, ActiveMQDefaultConfiguration.getDefaultRoutingType(), myQueue)
//            val consumer = session.createConsumer(myQueue, null, -1, -1, false)
//            consumer.setMessageHandler {
//                Thread.sleep(50) // 5x slower than the server producer
//                it.acknowledge()
//            }
//            val producer = session.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME)
//            session.start()
//
//            pollUntilClientNumber(server, 1)
//
//            val message = session.createMessage(false)
//            val request = RPCApi.ClientToServer.RpcRequest(
//                    clientAddress = SimpleString(myQueue),
//                    methodName = SlowConsumerRPCOps::streamAtInterval.name,
//                    serialisedArguments = listOf(10.millis, 123456).serialize(context = SerializationDefaults.RPC_SERVER_CONTEXT),
//                    replyId = Trace.InvocationId.newInstance(),
//                    sessionId = Trace.SessionId.newInstance()
//            )
//            request.writeToClientMessage(message)
//            message.putLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME, 0)
//            producer.send(message)
//            session.commit()
//
//            // We are consuming slower than the server is producing, so we should be kicked after a while
//            pollUntilClientNumber(server, 0)
//        }
//    }
//
//    @Test
//    fun `deduplication in the server`() {
//        rpcDriver {
//            val server = startRpcServer(ops = SlowConsumerRPCOpsImpl()).getOrThrow()
//
//            // Construct an RPC client session manually
//            val myQueue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.test.${random63BitValue()}"
//            val session = startArtemisSession(server.broker.hostAndPort!!)
//            session.createTemporaryQueue(myQueue, ActiveMQDefaultConfiguration.getDefaultRoutingType(), myQueue)
//            val consumer = session.createConsumer(myQueue, null, -1, -1, false)
//            val replies = ArrayList<Any>()
//            consumer.setMessageHandler {
//                replies.add(it)
//                it.acknowledge()
//            }
//
//            val producer = session.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME)
//            session.start()
//
//            pollUntilClientNumber(server, 1)
//
//            val message = session.createMessage(false)
//            val request = RPCApi.ClientToServer.RpcRequest(
//                    clientAddress = SimpleString(myQueue),
//                    methodName = DummyOps::protocolVersion.name,
//                    serialisedArguments = emptyList<Any>().serialize(context = SerializationDefaults.RPC_SERVER_CONTEXT),
//                    replyId = Trace.InvocationId.newInstance(),
//                    sessionId = Trace.SessionId.newInstance()
//            )
//            request.writeToClientMessage(message)
//            message.putLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME, 0)
//            producer.send(message)
//            // duplicate the message
//            producer.send(message)
//
//            pollUntilTrue("Number of replies is 1") {
//                replies.size == 1
//            }.getOrThrow()
//        }
//    }
//
//    @Test
//    fun `deduplication in the client`() {
//        rpcDriver {
//            val broker = startRpcBroker().getOrThrow()
//
//            // Construct an RPC server session manually
//            val session = startArtemisSession(broker.hostAndPort!!)
//            val consumer = session.createConsumer(RPCApi.RPC_SERVER_QUEUE_NAME)
//            val producer = session.createProducer()
//            val dedupeId = AtomicLong(0)
//            consumer.setMessageHandler {
//                it.acknowledge()
//                val request = RPCApi.ClientToServer.fromClientMessage(it)
//                when (request) {
//                    is RPCApi.ClientToServer.RpcRequest -> {
//                        val reply = RPCApi.ServerToClient.RpcReply(request.replyId, Try.Success(0), "server")
//                        val message = session.createMessage(false)
//                        reply.writeToClientMessage(SerializationDefaults.RPC_SERVER_CONTEXT, message)
//                        message.putLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME, dedupeId.getAndIncrement())
//                        producer.send(request.clientAddress, message)
//                        // duplicate the reply
//                        producer.send(request.clientAddress, message)
//                    }
//                    is RPCApi.ClientToServer.ObservablesClosed -> {
//                    }
//                }
//            }
//            session.start()
//
//            startRpcClient<RPCOps>(broker.hostAndPort!!).getOrThrow()
//        }
//    }
//}
//
//fun RPCDriverDSL.pollUntilClientNumber(server: RpcServerHandle, expected: Int) {
//    pollUntilTrue("number of RPC clients to become $expected") {
//        val clientAddresses = server.broker.serverControl.addressNames.filter { it.startsWith(RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX) }
//        clientAddresses.size == expected
//    }.get()
//}