/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.testing

import com.google.common.util.concurrent.ListenableFuture
import core.node.NodeConfiguration
import core.node.services.CityDatabase
import core.node.services.MockNetworkMapCache
import core.node.services.NodeInfo
import core.node.services.PhysicalLocation
import core.protocols.ProtocolLogic
import core.then
import core.utilities.ProgressTracker
import rx.Observable
import rx.subjects.PublishSubject
import java.nio.file.Path
import java.time.LocalDate
import java.util.*

/**
 * Base class for network simulations that are based on the unit test / mock environment.
 *
 * Sets up some nodes that can run protocols between each other, and exposes their progress trackers. Provides banks
 * in a few cities around the world.
 */
abstract class Simulation(val runAsync: Boolean,
                          val latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) {
    init {
        if (!runAsync && latencyInjector != null)
            throw IllegalArgumentException("The latency injector is only useful when using manual pumping.")
    }

    val bankLocations = listOf("London", "Frankfurt", "Rome")

    // This puts together a mock network of SimulatedNodes.

    open class SimulatedNode(dir: Path, config: NodeConfiguration, mockNet: MockNetwork,
                             withTimestamper: NodeInfo?) : MockNetwork.MockNode(dir, config, mockNet, withTimestamper) {
        override fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]
    }

    inner class BankFactory : MockNetwork.Factory {
        var counter = 0

        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, timestamperAddr: NodeInfo?): MockNetwork.MockNode {
            val letter = 'A' + counter
            val city = bankLocations[counter++ % bankLocations.size]
            val cfg = object : NodeConfiguration {
                // TODO: Set this back to "Bank of $city" after video day.
                override val myLegalName: String = "Bank $letter"
                override val exportJMXto: String = ""
                override val nearestCity: String = city
            }
            val node = SimulatedNode(dir, cfg, network, timestamperAddr)
            // TODO: This is obviously bogus: there should be a single network map for the whole simulated network.
            (node.services.networkMapCache as MockNetworkMapCache).ratesOracleNodes += ratesOracle.info
            (node.services.networkMapCache as MockNetworkMapCache).regulators += regulators.map { it.info }
            return node
        }

        fun createAll(): List<SimulatedNode> = bankLocations.map { network.createNode(timestamper.info, nodeFactory = this) as SimulatedNode }
    }

    val bankFactory = BankFactory()

    object TimestampingNodeFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, timestamperAddr: NodeInfo?): MockNetwork.MockNode {
            val cfg = object : NodeConfiguration {
                override val myLegalName: String = "Timestamping Service"   // A magic string recognised by the CP contract
                override val exportJMXto: String = ""
                override val nearestCity: String = "Zurich"
            }
            return SimulatedNode(dir, cfg, network, timestamperAddr)
        }
    }

    object RatesOracleFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, timestamperAddr: NodeInfo?): MockNetwork.MockNode {
            val cfg = object : NodeConfiguration {
                override val myLegalName: String = "Rates Service Provider"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Madrid"
            }

            val n = object : SimulatedNode(dir, cfg, network, timestamperAddr) {
                override fun makeInterestRatesOracleService() {
                    super.makeInterestRatesOracleService()
                    interestRatesService.upload(javaClass.getResourceAsStream("example.rates.txt"))
                    (services.networkMapCache as MockNetworkMapCache).ratesOracleNodes += info
                }
            }
            return n
        }
    }

    object RegulatorFactory : MockNetwork.Factory {
        override fun create(dir: Path, config: NodeConfiguration, network: MockNetwork, timestamperAddr: NodeInfo?): MockNetwork.MockNode {
            val cfg = object : NodeConfiguration {
                override val myLegalName: String = "Regulator A"
                override val exportJMXto: String = ""
                override val nearestCity: String = "Paris"
            }

            val n = object : SimulatedNode(dir, cfg, network, timestamperAddr) {
                // TODO: Regulatory nodes don't actually exist properly, this is a last minute demo request.
                //       So we just fire a message at a node that doesn't know how to handle it, and it'll ignore it.
                //       But that's fine for visualisation purposes.
            }
            return n
        }
    }

    val network = MockNetwork(false)

    val timestamper: SimulatedNode = network.createNode(null, nodeFactory = TimestampingNodeFactory) as SimulatedNode
    val ratesOracle: SimulatedNode = network.createNode(null, nodeFactory = RatesOracleFactory) as SimulatedNode
    val serviceProviders: List<SimulatedNode> = listOf(timestamper, ratesOracle)
    val banks: List<SimulatedNode> = bankFactory.createAll()
    val regulators: List<SimulatedNode> = listOf(network.createNode(null, nodeFactory = RegulatorFactory) as SimulatedNode)

    private val _allProtocolSteps = PublishSubject.create<Pair<SimulatedNode, ProgressTracker.Change>>()
    private val _doneSteps = PublishSubject.create<Collection<SimulatedNode>>()
    val allProtocolSteps: Observable<Pair<SimulatedNode, ProgressTracker.Change>> = _allProtocolSteps
    val doneSteps: Observable<Collection<SimulatedNode>> = _doneSteps

    private var pumpCursor = 0

    /**
     * The current simulated date. By default this never changes. If you want it to change, you should do so from
     * within your overridden [iterate] call. Changes in the current day surface in the [dateChanges] observable.
     */
    var currentDay: LocalDate = LocalDate.now()
        protected set(value) {
            field = value
            _dateChanges.onNext(value)
        }

    private val _dateChanges = PublishSubject.create<LocalDate>()
    val dateChanges: Observable<LocalDate> = _dateChanges

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
     */
    open fun iterate() {
        // Keep going until one of the nodes has something to do, or we have checked every node.
        val endpoints = network.messagingNetwork.endpoints
        var countDown = endpoints.size
        while (countDown > 0) {
            val handledMessage = endpoints[pumpCursor].pump(false)
            if (handledMessage) break
            // If this node had nothing to do, advance the cursor with wraparound and try again.
            pumpCursor = (pumpCursor + 1) % endpoints.size
            countDown--
        }
    }

    protected fun linkProtocolProgress(node: SimulatedNode, protocol: ProtocolLogic<*>) {
        val pt = protocol.progressTracker ?: return
        pt.changes.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            _allProtocolSteps.onNext(Pair(node, change))
        }
        // This isn't technically a "change" but it helps with UIs to send this notification of the first step.
        _allProtocolSteps.onNext(Pair(node, ProgressTracker.Change.Position(pt, pt.steps[1])))
    }

    protected fun linkConsensus(nodes: Collection<SimulatedNode>, protocol: ProtocolLogic<*>) {
        protocol.progressTracker?.changes?.subscribe { change: ProgressTracker.Change ->
            // Runs on node thread.
            if (protocol.progressTracker!!.currentStep == ProgressTracker.DONE) {
                _doneSteps.onNext(nodes)
            }
        }
    }

    open fun start() {
    }

    fun stop() {
        network.nodes.forEach { it.stop() }
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