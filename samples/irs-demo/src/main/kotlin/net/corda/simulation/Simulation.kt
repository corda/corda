package net.corda.simulation

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flatMap
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.CityDatabase
import net.corda.core.node.PhysicalLocation
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.containsType
import net.corda.core.then
import net.corda.core.utilities.ProgressTracker
import net.corda.irs.api.NodeInterestRates
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.TestNodeConfiguration
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.TestClock
import net.corda.testing.node.setTo
import rx.Observable
import rx.subjects.PublishSubject
import java.security.KeyPair
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Base class for network simulations that are based on the unit test / mock environment.
 *
 * Sets up some nodes that can run flows between each other, and exposes their progress trackers. Provides banks
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

    open class SimulatedNode(config: NodeConfiguration, mockNet: MockNetwork, networkMapAddress: SingleMessageRecipient?,
                             advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?)
        : MockNetwork.MockNode(config, mockNet, networkMapAddress, advertisedServices, id, keyPair) {
        override fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]
    }

    inner class BankFactory : MockNetwork.Factory {
        var counter = 0

        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            val letter = 'A' + counter
            val city = bankLocations[counter++ % bankLocations.size]

            val cfg = TestNodeConfiguration(
                    basedir = config.basedir,
                    // TODO: Set this back to "Bank of $city" after video day.
                    myLegalName = "Bank $letter",
                    nearestCity = city,
                    networkMapService = null)
            return SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, keyPair)
        }

        fun createAll(): List<SimulatedNode> {
            return bankLocations.map {
                network.createNode(networkMap.info.address, start = false, nodeFactory = this, keyPair = generateKeyPair()) as SimulatedNode
            }
        }
    }

    val bankFactory = BankFactory()

    object NetworkMapNodeFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork,
                            networkMapAddr: SingleMessageRecipient?, advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.containsType(NetworkMapService.type))
            val cfg = TestNodeConfiguration(
                    basedir = config.basedir,
                    myLegalName = "Network coordination center",
                    nearestCity = "Amsterdam",
                    networkMapService = null)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, keyPair) {}
        }
    }

    object NotaryNodeFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.containsType(SimpleNotaryService.type))
            val cfg = TestNodeConfiguration(
                    basedir = config.basedir,
                    myLegalName = "Notary Service",
                    nearestCity = "Zurich",
                    networkMapService = null)
            return SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, keyPair)
        }
    }

    object RatesOracleFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            require(advertisedServices.containsType(NodeInterestRates.type))
            val cfg = TestNodeConfiguration(
                    basedir = config.basedir,
                    myLegalName = "Rates Service Provider",
                    nearestCity = "Madrid",
                    networkMapService = null)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, keyPair) {
                override fun start(): MockNetwork.MockNode {
                    super.start()
                    javaClass.classLoader.getResourceAsStream("example.rates.txt").use {
                        databaseTransaction(database) {
                            findService<NodeInterestRates.Service>().upload(it)
                        }
                    }
                    return this
                }
            }
        }
    }

    object RegulatorFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, keyPair: KeyPair?): MockNetwork.MockNode {
            val cfg = TestNodeConfiguration(
                    basedir = config.basedir,
                    myLegalName = "Regulator A",
                    nearestCity = "Paris",
                    networkMapService = null)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, keyPair) {
                // TODO: Regulatory nodes don't actually exist properly, this is a last minute demo request.
                //       So we just fire a message at a node that doesn't know how to handle it, and it'll ignore it.
                //       But that's fine for visualisation purposes.
            }
        }
    }

    val network = MockNetwork(networkSendManuallyPumped, runAsync)
    // This one must come first.
    val networkMap: SimulatedNode
            = network.createNode(null, nodeFactory = NetworkMapNodeFactory, advertisedServices = ServiceInfo(NetworkMapService.type)) as SimulatedNode
    val notary: SimulatedNode
            = network.createNode(networkMap.info.address, nodeFactory = NotaryNodeFactory, advertisedServices = ServiceInfo(SimpleNotaryService.type)) as SimulatedNode
    val regulators: List<SimulatedNode> = listOf(network.createNode(networkMap.info.address, start = false, nodeFactory = RegulatorFactory) as SimulatedNode)
    val ratesOracle: SimulatedNode
            = network.createNode(networkMap.info.address, start = false, nodeFactory = RatesOracleFactory, advertisedServices = ServiceInfo(NodeInterestRates.type)) as SimulatedNode

    // All nodes must be in one of these two lists for the purposes of the visualiser tool.
    val serviceProviders: List<SimulatedNode> = listOf(notary, ratesOracle, networkMap)
    val banks: List<SimulatedNode> = bankFactory.createAll()

    val clocks = (serviceProviders + regulators + banks).map { it.services.clock as TestClock }

    // These are used from the network visualiser tool.
    private val _allFlowSteps = PublishSubject.create<Pair<SimulatedNode, ProgressTracker.Change>>()
    private val _doneSteps = PublishSubject.create<Collection<SimulatedNode>>()
    @Suppress("unused") val allFlowSteps: Observable<Pair<SimulatedNode, ProgressTracker.Change>> = _allFlowSteps
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
            node.smm.changes.filter { it.addOrRemove == AddOrRemove.ADD }.subscribe {
                linkFlowProgress(node, it.logic)
            }
        }
    }

    private fun linkFlowProgress(node: SimulatedNode, flow: FlowLogic<*>) {
        val pt = flow.progressTracker ?: return
        pt.changes.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            _allFlowSteps.onNext(Pair(node, change))
        }
    }


    protected fun showConsensusFor(nodes: List<SimulatedNode>) {
        val node = nodes.first()
        node.smm.changes.filter { it.addOrRemove == net.corda.node.utilities.AddOrRemove.ADD }.first().subscribe {
            linkConsensus(nodes, it.logic)
        }
    }

    private fun linkConsensus(nodes: Collection<SimulatedNode>, flow: FlowLogic<*>) {
        flow.progressTracker?.changes?.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            if (flow.progressTracker!!.currentStep == ProgressTracker.DONE) {
                _doneSteps.onNext(nodes)
            }
        }
    }

    val networkInitialisationFinished: ListenableFuture<*> =
            Futures.allAsList(network.nodes.map { it.networkMapRegistrationFuture })

    fun start(): ListenableFuture<Unit> {
        network.startNodes()
        // Wait for all the nodes to have finished registering with the network map service.
        return networkInitialisationFinished.flatMap { startMainSimulation() }
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
