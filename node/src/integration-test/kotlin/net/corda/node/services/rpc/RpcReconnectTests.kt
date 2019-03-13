package net.corda.node.services.rpc

import net.corda.client.rpc.*
import net.corda.core.contracts.Amount
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions
import net.corda.nodeapi.exceptions.RejectedCommandException
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.OutOfProcessImpl
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException
import org.junit.Test
import rx.Subscription
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.test.assertTrue

/**
 * This is a slow test.
 */
class RpcReconnectTests {

    companion object {
        private val log = contextLogger()

        /**
         * This function starts the flow returned by [flowStarter] and registers the [onFlowProgressEvent].
         * In case of connection error it makes a best effort to re-trigger the flow.
         * If the underlying node disconnects this function blocks until the connection is established.
         *
         * Note: Currently there is no support in the framework for querying the state of a flow that failed to start or to reconnect to a failed [FlowProgressHandle].
         *
         * Returns the [StateMachineRunId] of the started flow - or null if the status of the flow is unknown.
         * Note: Even if the returned [StateMachineRunId] is null, the flow could have still be started.
         * To determine its real state and retry, the client needs to write custom business logic.
         */
        fun <T> flowStartWithDisconnectHandling(
                reconnectingRPCConnection: ReconnectingRPCConnection,
                flowStarter: (CordaRPCOps) -> FlowProgressHandle<T>,
                onFlowProgressEvent: (StateMachineRunId, String) -> Unit
        ): StateMachineRunId? {

            val flowHandle: FlowProgressHandle<T> = try {
                flowStarter(reconnectingRPCConnection.proxy)
            } catch (e: Exception) {
                return when (e) {
                    is RejectedCommandException -> {
                        log.error("Node is being shutdown. Start flow rejected. Retrying when node is up.", e)
                        reconnectingRPCConnection.error(e)
                        flowStartWithDisconnectHandling(reconnectingRPCConnection, flowStarter, onFlowProgressEvent)
                    }
                    is ConnectionFailureException -> {
                        log.error("Failed to start flow. Connection dropped.", e)
                        reconnectingRPCConnection.error(e)
                        null
                    }
                    else -> {
                        log.error("Failed to start flow. ", e)
                        reconnectingRPCConnection.error(e)
                        null
                    }
                }
            }

            val flowId = flowHandle.id
            log.info("Started flow : $flowId")

            try {
                flowHandle.progress.subscribe(
                        { prg -> onFlowProgressEvent(flowId, prg) },
                        { error ->
                            // There is no way to recover at this point
                            log.error("!!!Error in subscribe: flow = $flowId, error : ${error.message} ")
                        },
                        { log.info("Finished flow $flowId.") })
            } catch (e: Exception) {
                log.error("Failed to register subscriber for flow = $flowId.", e)
                try {
                    flowHandle.close()
                } catch (e: Exception) {
                    // No action can be taken.
                }
                reconnectingRPCConnection.error(e)
            }

            return flowId
        }

        /**
         * This is a blocking method that subscribes the [observer], takes a callback [onEvent] and performs reconnects.
         * It should be run in its own thread.
         *
         * It can be externally stopped by calling [ObserverNotifier.stop].
         */
        tailrec fun <A, B> resilientObservation(
                reconnectingRPCConnection: ReconnectingRPCConnection,
                observer: (CordaRPCOps) -> DataFeed<A, B>,
                onEvent: (B) -> Unit,
                onSnapshot: (A) -> Unit = {},
                observerNotifier: ObserverNotifier = ObserverNotifier()) {
            val feed: DataFeed<A, B>? = try {
                observer(reconnectingRPCConnection.proxy)
            } catch (e: Exception) {
                // Perform re-connect.
                log.error("Error running the observer", e)
                null
            }

            // Attempt to subscribe.
            var subscriptionError: Exception? = null
            val subscription: Subscription? = feed?.let {
                onSnapshot(it.snapshot)
                try {
                    it.updates.subscribe(
                            onEvent,
                            {
                                log.error("!!!Error in subscribe. ${it.message}")
                                observerNotifier.fail(it)
                            },
                            observerNotifier::stop)
                } catch (e: Exception) {
                    log.error("Failed to register subscriber .", e)
                    subscriptionError = e
                    null
                }
            }?.also {
                log.info("Successfully subscribed.")
            }

            // Wait until the subscription finishes.
            val observerError = subscriptionError ?: subscription?.let {
                observerNotifier.await().also {
                    // Terminate subscription such that nothing gets past this point to downstream Observables.
                    subscription.unsubscribe()
                }
            } ?: return

            // Only continue if the subscription failed.
            reconnectingRPCConnection.error(observerError)
            return resilientObservation(reconnectingRPCConnection, observer, onEvent, onSnapshot, observerNotifier.reset())
        }
    }

    /**
     * Helper class useful for reconnecting to a Node.
     */
    data class ReconnectingRPCConnection(
            val nodeHostAndPort: NetworkHostAndPort,
            val username: String,
            val password: String
    ) : RPCConnection<CordaRPCOps> {
        private var currentRPCConnection: CordaRPCConnection? = null

        enum class CurrentState {
            UNCONNECTED, CONNECTED, CONNECTING, CLOSED, DIED
        }

        private var currentState = CurrentState.UNCONNECTED

        constructor(nodeHostAndPort: NetworkHostAndPort, username: String, password: String, existingRPCConnection: CordaRPCConnection) : this(nodeHostAndPort, username, password) {
            this.currentRPCConnection = existingRPCConnection
            currentState = CurrentState.CONNECTED
        }

        private val current: CordaRPCConnection
            @Synchronized get() = when (currentState) {
                CurrentState.CONNECTED -> currentRPCConnection!!
                CurrentState.UNCONNECTED, CurrentState.CLOSED -> {
                    currentState = CurrentState.CONNECTING
                    currentRPCConnection = establishConnectionWithRetry()
                    currentState = CurrentState.CONNECTED
                    currentRPCConnection!!
                }
                CurrentState.CONNECTING, CurrentState.DIED -> throw IllegalArgumentException("Illegal state")
            }

        /**
         * Called on external error.
         * Will block until the connection is established again.
         */
        @Synchronized
        fun error(e: Throwable) {
            currentState = CurrentState.DIED
            //TODO - handle error cases
            log.error("Reconnecting to ${this.nodeHostAndPort} due to error: ${e.message}")
            currentState = CurrentState.CONNECTING
            currentRPCConnection = establishConnectionWithRetry()
            currentState = CurrentState.CONNECTED
        }

        // TODO - use exponential backoff for the retry interval
        private tailrec fun establishConnectionWithRetry(retryInterval: Duration = 5.seconds): CordaRPCConnection {
            log.info("Connecting to: $nodeHostAndPort")
            try {
                return CordaRPCClient(
                        nodeHostAndPort, CordaRPCClientConfiguration(connectionMaxRetryInterval = retryInterval)
                ).start(username, password).also {
                    // Check connection is truly operational before returning it.
                    require(it.proxy.nodeInfo().legalIdentitiesAndCerts.isNotEmpty()) {
                        "Could not establish connection to ${nodeHostAndPort}."
                    }
                    log.info("Connection successfully established with: ${nodeHostAndPort}")
                }
            } catch (ex: Exception) {
                when (ex) {
                    is ActiveMQSecurityException -> {
                        // Happens when incorrect credentials provided.
                        // It can happen at startup as well when the credentials are correct.
                        // TODO - add a counter to only retry 2-3 times on security exceptions,
                    }
                    is RPCException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.info("Exception upon establishing connection: ${ex.message}")
                    }
                    is ActiveMQConnectionTimedOutException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.info("Exception upon establishing connection: ${ex.message}")
                    }
                    is ActiveMQUnBlockedException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.info("Exception upon establishing connection: ${ex.message}")
                    }
                    else -> {
                        log.info("Unknown exception upon establishing connection.", ex)
                    }
                }
            }

            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
            return establishConnectionWithRetry(retryInterval)
        }

        override val proxy
            get() = current.proxy

        override val serverProtocolVersion
            get() = current.serverProtocolVersion

        @Synchronized
        override fun notifyServerAndClose() {
            currentState = CurrentState.CLOSED
            currentRPCConnection!!.notifyServerAndClose()
        }

        @Synchronized
        override fun forceClose() {
            currentState = CurrentState.CLOSED
            currentRPCConnection!!.forceClose()
        }

        @Synchronized
        override fun close() {
            currentState = CurrentState.CLOSED
            currentRPCConnection!!.close()
        }
    }

    /**
     * Utility to externally control a registered subscription.
     */
    class ObserverNotifier {
        private val terminated = LinkedBlockingQueue<Optional<Throwable>>(1)

        fun stop() = terminated.put(Optional.empty())
        fun fail(e: Throwable) = terminated.put(Optional.of(e))

        /**
         * Returns null if the observation ended successfully.
         */
        internal fun await(): Throwable? = terminated.poll(100, TimeUnit.MINUTES).orElse(null)

        internal fun reset(): ObserverNotifier = this.also { it.terminated.clear() }
    }

    @Synchronized
    fun MutableMap<StateMachineRunId, MutableList<String>>.addEvent(id: StateMachineRunId, progress: String): Boolean {
        return getOrPut(id) { mutableListOf() }.add(progress)
    }

    /**
     * This test showcases a pattern for making the RPC client reconnect.
     *
     * Note that during node failure events can be lost and starting flows can become unreliable.
     * The purpose of this test and utilities is to handle reconnects and make best efforts to retry.
     *
     * This test runs flows in a loop and in the background kills the node or restarts it.
     * Also the RPC connection is made through a proxy that introduces random latencies and is also periodically killed.
     */
    @Test
    fun `Test that the RPC client is able to reconnect and proceed after node failure, restart, or connection reset`() {
        val nrOfFlowsToRun = 450 // Takes around 5 minutes.
        val nodeRunningTime = { Random().nextInt(12000) + 8000 }

        val demoUser = User("demo", "demo", setOf(Permissions.all()))

        val nodePort = 20006
        val proxyPort = 20007

        // When this reaches 0 - the test will end.
        val flowsCountdownLatch = CountDownLatch(nrOfFlowsToRun)

        // These are the expected progress steps for the CashIssueAndPayFlow.
        val expectedProgress = listOf(
                "Starting",
                "Issuing cash",
                "Generating transaction",
                "Signing transaction",
                "Finalising transaction",
                "Broadcasting transaction to participants",
                "Paying recipient",
                "Generating anonymous identities",
                "Generating transaction",
                "Signing transaction",
                "Finalising transaction",
                "Requesting signature by notary service",
                "Requesting signature by Notary service",
                "Validating response from Notary service",
                "Broadcasting transaction to participants",
                "Done"
        )

        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            fun startBankA() = startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("rpcSettings.address" to "localhost:$nodePort"))

            var (bankA, bankB) = listOf(
                    startBankA(),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser))
            ).transpose().getOrThrow()

            val notary = defaultNotaryIdentity
            val bankAConnection = ReconnectingRPCConnection(bankA.rpcAddress.copy(port = proxyPort), demoUser.username, demoUser.password)
            val tcpProxy = RandomFailingProxy(serverPort = proxyPort, remotePort = nodePort).start()

            // Start nrOfFlowsToRun flows in the background.
            var flowProgressEvents: Map<StateMachineRunId, List<String>>? = null
            thread(name = "Flow feeder") { flowProgressEvents = runTestFlows(nrOfFlowsToRun, flowsCountdownLatch, bankAConnection, bankB, notary) }

            // Observe the vault.
            val vaultObserverNotifier = ObserverNotifier()
            var vaultEvents: List<Vault.Update<Cash.State>>? = null
            thread(name = "Vault observer") { vaultEvents = observeVaultForCash(bankAConnection, vaultObserverNotifier) }

            // Observe the stateMachine.
            val stateMachineNotifier = ObserverNotifier()
            var stateMachineEvents: List<StateMachineUpdate>? = null
            thread(name = "State machine observer") { stateMachineEvents = observeStateMachine(bankAConnection, stateMachineNotifier) }

            // While the flows are running, randomly apply a different failure scenario.
            val nrRestarts = AtomicInteger()
            thread(name = "Node killer") {
                while (true) {
                    if (flowsCountdownLatch.count == 0L) break

                    // Let the node run for a random time interval.
                    nodeRunningTime().also { ms ->
                        log.info("Running node for ${ms / 1000} s.")
                        Thread.sleep(ms.toLong())
                    }

                    if (flowsCountdownLatch.count == 0L) break

                    when (Random().nextInt().rem(6).absoluteValue) {
                        0 -> {
                            log.info("Forcefully killing node and proxy.")
                            (bankA as OutOfProcessImpl).onStopCallback()
                            (bankA as OutOfProcess).process.destroyForcibly()
                            tcpProxy.stop()
                            bankA = startBankA().get()
                            tcpProxy.start()
                        }
                        1 -> {
                            log.info("Forcefully killing node.")
                            (bankA as OutOfProcessImpl).onStopCallback()
                            (bankA as OutOfProcess).process.destroyForcibly()
                            bankA = startBankA().get()
                        }
                        2 -> {
                            log.info("Shutting down node.")
                            bankA.stop()
                            tcpProxy.stop()
                            bankA = startBankA().get()
                            tcpProxy.start()
                        }
                        3, 4 -> {
                            log.info("Killing proxy.")
                            tcpProxy.stop()
                            Thread.sleep(Random().nextInt(5000).toLong())
                            tcpProxy.start()
                        }
                        5 -> {
                            log.info("Dropping connection.")
                            tcpProxy.failConnection()
                        }
                    }
                    nrRestarts.incrementAndGet()
                }
            }

            // Wait until all flows have been started.
            flowsCountdownLatch.await()

            // Wait for all events to come in.
            Thread.sleep(5000)

            // Stop the vault observer.
            vaultObserverNotifier.stop()
            stateMachineNotifier.stop()
            Thread.sleep(1000)

            val nrFailures = nrRestarts.get()
            log.info("Checking results after $nrFailures restarts.")

            // The only time when flows can be left without any status is when the node died exactly when that flow was started.
            assertTrue(flowProgressEvents!!.size + nrFailures >= nrOfFlowsToRun, "Not all flows were triggered")

            // The progress status for each flow can only miss the last events, because the node might have been killed.
            val missingProgressEvents = flowProgressEvents!!.filterValues { expectedProgress.subList(0, it.size - 1) == it }
            assertTrue(missingProgressEvents.isEmpty(), "The flow progress tracker is missing events: $missingProgressEvents")

            // Check that enough vault events were received.
            // This check is fuzzy because events can go missing during node restarts.
            // Ideally there should be nrOfFlowsToRun events receive but some might get lost for each restart.
            assertTrue(vaultEvents!!.size + nrFailures * 3 >= nrOfFlowsToRun, "Not all vault events were received")

            // Query the vault and check that states were created for all confirmed flows

            val allCashStates = bankAConnection.proxy
                    .vaultQueryByWithPagingSpec(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED), PageSpecification(1, 10000))
                    .states

            assertTrue(allCashStates.size >= flowProgressEvents!!.size, "Not all flows were executed successfully")

            // Check that no flow was triggered twice.
            val duplicates = allCashStates.groupBy { it.state.data.amount }.filterValues { it.size > 1 }
            assertTrue(duplicates.isEmpty(), "${duplicates.size} flows were retried illegally.")

            log.info("SM EVENTS: ${stateMachineEvents!!.size}")
            // State machine events are very likely to get lost more often because they seem to be sent with a delay.
            assertTrue(stateMachineEvents!!.count { it is StateMachineUpdate.Added } > nrOfFlowsToRun / 2, "Too many Added state machine events lost.")
            assertTrue(stateMachineEvents!!.count { it is StateMachineUpdate.Removed } > nrOfFlowsToRun / 2, "Too many Removed state machine events lost.")

            tcpProxy.close()
            bankAConnection.forceClose()
        }
    }

    /**
     * This function runs [nrOfFlowsToRun] flows and returns the progress of each one of these flows.
     */
    private fun runTestFlows(nrOfFlowsToRun: Int, flowsCountdownLatch: CountDownLatch, bankAConnection: ReconnectingRPCConnection, bankB: NodeHandle, notary: Party): Map<StateMachineRunId, List<String>> {
        val baseAmount = Amount.parseCurrency("0 USD")
        val issuerRef = OpaqueBytes.of(0x01)

        val flowProgressEvents: MutableMap<StateMachineRunId, MutableList<String>> = mutableMapOf()
        val flowIds: MutableList<StateMachineRunId?> = mutableListOf()

        for (i in (1..nrOfFlowsToRun)) {
            log.info("Starting flow $i")
            val result = flowStartWithDisconnectHandling(bankAConnection,
                    flowStarter = { rpc ->
                        rpc.startTrackedFlowDynamic(
                                CashIssueAndPaymentFlow::class.java,
                                baseAmount.plus(Amount.parseCurrency("$i USD")),
                                issuerRef,
                                bankB.nodeInfo.legalIdentities.first(),
                                false,
                                notary
                        )
                    },
                    onFlowProgressEvent = { id, prog ->
                        flowProgressEvents.addEvent(id, prog)
                        log.info("Progress $id : $prog")
                    }
            )

            flowIds += result
            flowsCountdownLatch.countDown()
        }

        return flowProgressEvents
    }

    /**
     * Blocking function that observes the vault and returns the result.
     */
    fun observeVaultForCash(bankAConnection: ReconnectingRPCConnection, observerNotifier: ObserverNotifier): List<Vault.Update<Cash.State>> {
        val vaultEvents = Collections.synchronizedList(mutableListOf<Vault.Update<Cash.State>>())

        resilientObservation(bankAConnection,
                { rpc ->
                    rpc.vaultTrackByWithPagingSpec(
                            Cash.State::class.java,
                            QueryCriteria.VaultQueryCriteria(),
                            PageSpecification(1, 1)
                    )
                },
                { update: Vault.Update<Cash.State> ->
                    log.info("vault update produced ${update.produced.map { it.state.data.amount }} consumed ${update.consumed.map { it.ref }}")
                    vaultEvents.add(update)
                },
                {},
                observerNotifier
        )

        return vaultEvents
    }

    /**
     * Blocking function that observes the state machine and returns the result.
     */
    fun observeStateMachine(bankAConnection: ReconnectingRPCConnection, observerNotifier: ObserverNotifier): List<StateMachineUpdate> {
        val smEvents = Collections.synchronizedList(mutableListOf<StateMachineUpdate>())

        resilientObservation(bankAConnection,
                { rpc ->
                    rpc.stateMachinesFeed()
                },
                { update ->
                    log.info(update.toString())
                    smEvents.add(update)
                },
                {}
                ,
                observerNotifier
        )

        return smEvents
    }

    /**
     * Simple proxy that can be restarted and introduces random latencies.
     * This also acts as a mock load balancer.
     */
    class RandomFailingProxy(val serverPort: Int, val remotePort: Int) {
        private val threadPool = Executors.newCachedThreadPool()
        private val stopCopy = AtomicBoolean(false)
        private var currentServerSocket: ServerSocket? = null
        private val rnd = ThreadLocal.withInitial { Random() }

        fun start(): RandomFailingProxy {
            stopCopy.set(false)
            currentServerSocket = ServerSocket(serverPort)
            threadPool.execute {
                try {
                    currentServerSocket.use { serverSocket ->
                        while (!stopCopy.get() && !serverSocket!!.isClosed) {
                            handleConnection(serverSocket.accept())
                        }
                    }
                } catch (e: SocketException) {
                    // The Server socket could be closed
                }
            }
            return this
        }

        private fun handleConnection(socket: Socket) {
            threadPool.execute {
                socket.use { _ ->
                    try {
                        Socket("localhost", remotePort).use { target ->
                            // send message to node
                            threadPool.execute {
                                try {
                                    socket.getInputStream().slowCopyTo(target.getOutputStream())
                                } catch (e: IOException) {
                                    // Thrown when the connection to the target server dies.
                                }
                            }
                            target.getInputStream().slowCopyTo(socket.getOutputStream())
                        }
                    } catch (e: IOException) {
                        // Thrown when the connection to the target server dies.
                    }
                }
            }
        }

        fun stop(): RandomFailingProxy {
            stopCopy.set(true)
            currentServerSocket?.close()
            return this
        }

        private val failOneConnection = AtomicBoolean(false)
        fun failConnection() {
            failOneConnection.set(true)
        }

        fun close() {
            try {
                stop()
                threadPool.shutdownNow()
            } catch (e: Exception) {
                // Nothing can be done.
            }
        }

        private fun InputStream.slowCopyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
            var bytesCopied: Long = 0
            val buffer = ByteArray(bufferSize)
            var bytes = read(buffer)
            while (bytes >= 0 && !stopCopy.get()) {
                // Introduce intermittent slowness.
                if (rnd.get().nextInt().rem(700) == 0) {
                    Thread.sleep(rnd.get().nextInt(2000).toLong())
                }
                if (failOneConnection.compareAndSet(true, false)) {
                    throw IOException("Randomly dropped one connection")
                }
                out.write(buffer, 0, bytes)
                bytesCopied += bytes
                bytes = read(buffer)
            }
            return bytesCopied
        }
    }
}
