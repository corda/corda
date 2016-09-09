package com.r3corda.simulation

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.CityDatabase
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.PhysicalLocation
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.then
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.demos.api.NodeInterestRates
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.testing.node.InMemoryMessagingNetwork
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.TestClock
import com.r3corda.testing.node.setTo
import rx.Observable
import rx.subjects.PublishSubject
import java.nio.file.Path
import java.security.KeyPair
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Base class for network simulations that are based on the unit test / mock environment.
 *
 * Sets up some nodes that can run protocols between each other, and exposes their progress trackers. Provides banks
 * in a few cities around the world.
 */
abstract class Simulation(val networkSendManuallyPumped: Boolean,
                          runAsync: Boolean,
                          latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) {
    init {
        if (!runAsync && latencyInjector != null)
            throw IllegalArgumentException("The latency injector is only useful when using manual pumping.")
    }

    val bankLocations = listOf("London", "Frankfurt", "Rome")

    // This puts together a mock network of SimulatedNodes.

    open class SimulatedNode(dir: Path, config: NodeConfiguration, mockNet: MockNetwork, networkMapAddress: SingleMessageRecipient?,
                             advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?) : MockNetwork.MockNode(dir, config, mockNet, networkMapAddress, advertisedServices, id, keyPair) {
        override fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]
    }

    inner class BankFactory : MockNetwork.Factory {
        var counter = 0

        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            val letter = 'A' + counter
            val city = bankLocations[counter++ % bankLocations.size]
            val cfg = object : com.r3corda.node.services.config.NodeConfiguration {
                // TODO: Set this back to "Bank of $city" after video day.
                override val myLegalName: String = "Bank $letter"
                override val exportJMXto: String = ""
                override val nearestCity: String = city
                override val keyStorePassword: String = "dummy"
                override val trustStorePassword: String = "trustpass"
            }
            return SimulatedNode(dir, cfg, network, networkMapAddr, advertisedServices, id, keyPair)
        }

        fun createAll(): List<SimulatedNode> = bankLocations.
                map { network.createNode(networkMap.info.address, start = false, nodeFactory = this) as SimulatedNode }
    }

    val bankFactory = BankFactory()

    object NetworkMapNodeFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: com.r3corda.node.services.config.NodeConfiguration, network: MockNetwork,
                            networkMapAddr: SingleMessageRecipient?, advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.contains(com.r3corda.node.services.network.NetworkMapService.Type))
            val cfg = object : com.r3corda.node.services.config.NodeConfiguration {
                override val myLegalName: String = "Network coordination center"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Amsterdam"
                override val keyStorePassword: String = "dummy"
                override val trustStorePassword: String = "trustpass"
            }

            return object : SimulatedNode(dir, cfg, network, networkMapAddr, advertisedServices, id, keyPair) {}
        }
    }

    object NotaryNodeFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: com.r3corda.node.services.config.NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.contains(com.r3corda.node.services.transactions.SimpleNotaryService.Type))
            val cfg = object : com.r3corda.node.services.config.NodeConfiguration {
                override val myLegalName: String = "Notary Service"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Zurich"
                override val keyStorePassword: String = "dummy"
                override val trustStorePassword: String = "trustpass"
            }
            return SimulatedNode(dir, cfg, network, networkMapAddr, advertisedServices, id, keyPair)
        }
    }

    object RatesOracleFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: com.r3corda.node.services.config.NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.contains(NodeInterestRates.Type))
            val cfg = object : com.r3corda.node.services.config.NodeConfiguration {
                override val myLegalName: String = "Rates Service Provider"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Madrid"
                override val keyStorePassword: String = "dummy"
                override val trustStorePassword: String = "trustpass"
            }

            return object : SimulatedNode(dir, cfg, network, networkMapAddr, advertisedServices, id, keyPair) {
                override fun start(): MockNetwork.MockNode {
                    super.start()
                    findService<NodeInterestRates.Service>().upload(javaClass.getResourceAsStream("example.rates.txt"))
                    return this
                }
            }
        }
    }

    object RegulatorFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: com.r3corda.node.services.config.NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceType>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            val cfg = object : com.r3corda.node.services.config.NodeConfiguration {
                override val myLegalName: String = "Regulator A"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Paris"
                override val keyStorePassword: String = "dummy"
                override val trustStorePassword: String = "trustpass"
            }

            val n = object : SimulatedNode(dir, cfg, network, networkMapAddr, advertisedServices, id, keyPair) {
                // TODO: Regulatory nodes don't actually exist properly, this is a last minute demo request.
                //       So we just fire a message at a node that doesn't know how to handle it, and it'll ignore it.
                //       But that's fine for visualisation purposes.
            }
            return n
        }
    }

    val network = MockNetwork(networkSendManuallyPumped, runAsync)
    // This one must come first.
    val networkMap: SimulatedNode
            = network.createNode(null, nodeFactory = NetworkMapNodeFactory, advertisedServices = com.r3corda.node.services.network.NetworkMapService.Type) as SimulatedNode
    val notary: SimulatedNode
            = network.createNode(networkMap.info.address, nodeFactory = NotaryNodeFactory, advertisedServices = com.r3corda.node.services.transactions.SimpleNotaryService.Type) as SimulatedNode
    val regulators: List<SimulatedNode> = listOf(network.createNode(networkMap.info.address, start = false, nodeFactory = RegulatorFactory) as SimulatedNode)
    val ratesOracle: SimulatedNode
            = network.createNode(networkMap.info.address, start = false, nodeFactory = RatesOracleFactory, advertisedServices = NodeInterestRates.Type) as SimulatedNode

    // All nodes must be in one of these two lists for the purposes of the visualiser tool.
    val serviceProviders: List<SimulatedNode> = listOf(notary, ratesOracle, networkMap)
    val banks: List<SimulatedNode> = bankFactory.createAll()

    val clocks = (serviceProviders + regulators + banks).map { it.services.clock as TestClock }

    // These are used from the network visualiser tool.
    private val _allProtocolSteps = PublishSubject.create<Pair<SimulatedNode, ProgressTracker.Change>>()
    private val _doneSteps = PublishSubject.create<Collection<SimulatedNode>>()
    @Suppress("unused") val allProtocolSteps: Observable<Pair<SimulatedNode, ProgressTracker.Change>> = _allProtocolSteps
    @Suppress("unused") val doneSteps: Observable<Collection<SimulatedNode>> = _doneSteps

    private var pumpCursor = 0

    /**
     * The current simulated date. By default this never changes. If you want it to change, you should do so from
     * within your overridden [iterate] call. Changes in the current day surface in the [dateChanges] observable.
     */
    var currentDateAndTime: LocalDateTime = LocalDate.now().atStartOfDay()
        protected set(value) {
            field = value
            _dateChanges.onNext(value)
        }

    private val _dateChanges = PublishSubject.create<LocalDateTime>()
    val dateChanges: Observable<LocalDateTime> get() = _dateChanges

    init {
        // Advance node clocks when current time is changed
        dateChanges.subscribe {
            clocks.setTo(currentDateAndTime.toInstant(ZoneOffset.UTC))
        }
    }

    /**
     * A place for simulations to stash human meaningful text about what the node is "thinking", which might appear
     * in the UI somewhere.
     */
    val extraNodeLabels = Collections.synchronizedMap(HashMap<SimulatedNode, String>())

    /**
     * Iterates the simulation by one step.
     *
     * The default implementation circles around the nodes, pumping until one of them handles a message. The next call
     * will carry on from where this one stopped. In an environment where you want to take actions between anything
     * interesting happening, or control the precise speed at which things operate (beyond the latency injector), this
     * is a useful way to do things.
     *
     * @return the message that was processed, or null if no node accepted a message in this round.
     */
    open fun iterate(): InMemoryMessagingNetwork.MessageTransfer? {

        if (networkSendManuallyPumped) {
            network.messagingNetwork.pumpSend(false)
        }

        // Keep going until one of the nodes has something to do, or we have checked every node.
        val endpoints = network.messagingNetwork.endpoints
        var countDown = endpoints.size
        while (countDown > 0) {
            val handledMessage = endpoints[pumpCursor].pumpReceive(false)
            if (handledMessage != null)
                return handledMessage
            // If this node had nothing to do, advance the cursor with wraparound and try again.
            pumpCursor = (pumpCursor + 1) % endpoints.size
            countDown--
        }
        return null
    }

    protected fun showProgressFor(nodes: List<SimulatedNode>) {
        nodes.forEach { node ->
            node.smm.changes.filter { it.second == com.r3corda.node.utilities.AddOrRemove.ADD }.first().subscribe {
                linkProtocolProgress(node, it.first)
            }
        }
    }

    private fun linkProtocolProgress(node: SimulatedNode, protocol: ProtocolLogic<*>) {
        val pt = protocol.progressTracker ?: return
        pt.changes.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            _allProtocolSteps.onNext(Pair(node, change))
        }
        // This isn't technically a "change" but it helps with UIs to send this notification of the first step.
        _allProtocolSteps.onNext(Pair(node, ProgressTracker.Change.Position(pt, pt.steps[1])))
    }


    protected fun showConsensusFor(nodes: List<SimulatedNode>) {
        val node = nodes.first()
        node.smm.changes.filter { it.second == com.r3corda.node.utilities.AddOrRemove.ADD }.first().subscribe {
            linkConsensus(nodes, it.first)
        }
    }

    private fun linkConsensus(nodes: Collection<SimulatedNode>, protocol: ProtocolLogic<*>) {
        protocol.progressTracker?.changes?.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            if (protocol.progressTracker!!.currentStep == ProgressTracker.DONE) {
                _doneSteps.onNext(nodes)
            }
        }
    }

    @Suppress("unused")   // Used from the network visualiser tool.
    val networkInitialisationFinished: ListenableFuture<*> =
            Futures.allAsList(network.nodes.map { it.networkMapRegistrationFuture })

    fun start(): ListenableFuture<Unit> {
        network.startNodes()
        // Wait for all the nodes to have finished registering with the network map service.
        val startup: ListenableFuture<List<Unit>> = Futures.allAsList(network.nodes.map { it.networkMapRegistrationFuture })
        return Futures.transformAsync(startup) { l: List<Unit>? -> startMainSimulation() }
    }

    /**
     * Sub-classes should override this to trigger whatever they want to simulate. This method will be invoked once the
     * network bringup has been simulated.
     */
    protected open fun startMainSimulation(): ListenableFuture<Unit> {
        return Futures.immediateFuture(Unit)
    }

    fun stop() {
        network.stopNodes()
    }

    /**
     * Given a function that returns a future, iterates that function with arguments like (0, 1), (1, 2), (2, 3) etc
     * each time the returned future completes.
     */
    fun startTradingCircle(tradeBetween: (indexA: Int, indexB: Int) -> ListenableFuture<*>) {
        fun next(i: Int, j: Int) {
            tradeBetween(i, j).then {
                val ni = (i + 1) % banks.size
                val nj = (j + 1) % banks.size
                next(ni, nj)
            }
        }
        next(0, 1)
    }
}
