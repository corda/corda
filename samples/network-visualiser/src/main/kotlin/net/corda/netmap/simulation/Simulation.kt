package net.corda.netmap.simulation

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.utils.CityDatabase
import net.corda.finance.utils.WorldMapLocation
import net.corda.irs.api.NodeInterestRates
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.nodeapi.internal.ServiceType
import net.corda.testing.DUMMY_MAP
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_REGULATOR
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.TestClock
import net.corda.testing.node.setTo
import net.corda.testing.setCordappPackages
import net.corda.testing.testNodeConfiguration
import rx.Observable
import rx.subjects.PublishSubject
import java.math.BigInteger
import java.security.KeyPair
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.Future

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

    val bankLocations = listOf(Pair("London", "GB"), Pair("Frankfurt", "DE"), Pair("Rome", "IT"))

    // This puts together a mock network of SimulatedNodes.

    open class SimulatedNode(config: NodeConfiguration, mockNet: MockNetwork, networkMapAddress: SingleMessageRecipient?,
                             advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                             entropyRoot: BigInteger)
        : MockNetwork.MockNode(config, mockNet, networkMapAddress, advertisedServices, id, notaryIdentity, entropyRoot) {
        override val started: StartedNode<SimulatedNode>? get() = uncheckedCast(super.started)
        override fun findMyLocation(): WorldMapLocation? {
            return configuration.myLegalName.locality.let { CityDatabase[it] }
        }
    }

    inner class BankFactory : MockNetwork.Factory<SimulatedNode> {
        var counter = 0

        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): SimulatedNode {
            val letter = 'A' + counter
            val (city, country) = bankLocations[counter++ % bankLocations.size]

            val cfg = testNodeConfiguration(
                    baseDirectory = config.baseDirectory,
                    myLegalName = CordaX500Name(organisation = "Bank $letter", locality = city, country = country))
            return SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot)
        }

        fun createAll(): List<SimulatedNode> {
            return bankLocations.mapIndexed { i, _ ->
                // Use deterministic seeds so the simulation is stable. Needed so that party owning keys are stable.
                mockNet.createUnstartedNode(nodeFactory = this, entropyRoot = BigInteger.valueOf(i.toLong()))
            }
        }
    }

    val bankFactory = BankFactory()

    object NetworkMapNodeFactory : MockNetwork.Factory<SimulatedNode> {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): SimulatedNode {
            val cfg = testNodeConfiguration(
                    baseDirectory = config.baseDirectory,
                    myLegalName = DUMMY_MAP.name)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot) {}
        }
    }

    object NotaryNodeFactory : MockNetwork.Factory<SimulatedNode> {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): SimulatedNode {
            require(advertisedServices.containsType(SimpleNotaryService.type))
            val cfg = testNodeConfiguration(
                    baseDirectory = config.baseDirectory,
                    myLegalName = DUMMY_NOTARY.name)
            return SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot)
        }
    }

    object RatesOracleFactory : MockNetwork.Factory<SimulatedNode> {
        // TODO: Make a more realistic legal name
        val RATES_SERVICE_NAME = CordaX500Name(organisation = "Rates Service Provider", locality = "Madrid", country = "ES")

        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): SimulatedNode {
            val cfg = testNodeConfiguration(
                    baseDirectory = config.baseDirectory,
                    myLegalName = RATES_SERVICE_NAME)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot) {
                override fun start() = super.start().apply {
                    registerInitiatedFlow(NodeInterestRates.FixQueryHandler::class.java)
                    registerInitiatedFlow(NodeInterestRates.FixSignHandler::class.java)
                    javaClass.classLoader.getResourceAsStream("net/corda/irs/simulation/example.rates.txt").use {
                        database.transaction {
                            installCordaService(NodeInterestRates.Oracle::class.java).uploadFixes(it.reader().readText())
                        }
                    }
                }
            }
        }
    }

    object RegulatorFactory : MockNetwork.Factory<SimulatedNode> {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): SimulatedNode {
            val cfg = testNodeConfiguration(
                    baseDirectory = config.baseDirectory,
                    myLegalName = DUMMY_REGULATOR.name)
            return object : SimulatedNode(cfg, network, networkMapAddr, advertisedServices, id, notaryIdentity, entropyRoot) {
                // TODO: Regulatory nodes don't actually exist properly, this is a last minute demo request.
                //       So we just fire a message at a node that doesn't know how to handle it, and it'll ignore it.
                //       But that's fine for visualisation purposes.
            }
        }
    }

    val mockNet = MockNetwork(networkSendManuallyPumped, runAsync)
    // This one must come first.
    val networkMap = mockNet.startNetworkMapNode(nodeFactory = NetworkMapNodeFactory)
    val notary = mockNet.createNode(nodeFactory = NotaryNodeFactory, advertisedServices = ServiceInfo(SimpleNotaryService.type))
    val regulators = listOf(mockNet.createUnstartedNode(nodeFactory = RegulatorFactory))
    val ratesOracle = mockNet.createUnstartedNode(nodeFactory = RatesOracleFactory)

    // All nodes must be in one of these two lists for the purposes of the visualiser tool.
    val serviceProviders: List<SimulatedNode> = listOf(notary.internals, ratesOracle, networkMap.internals)
    val banks: List<SimulatedNode> = bankFactory.createAll()

    val clocks = (serviceProviders + regulators + banks).map { it.platformClock as TestClock }

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
    val extraNodeLabels: MutableMap<SimulatedNode, String> = Collections.synchronizedMap(HashMap())

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
            mockNet.messagingNetwork.pumpSend(false)
        }

        // Keep going until one of the nodes has something to do, or we have checked every node.
        val endpoints = mockNet.messagingNetwork.endpoints
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

    protected fun showProgressFor(nodes: List<StartedNode<SimulatedNode>>) {
        nodes.forEach { node ->
            node.smm.changes.filter { it is StateMachineManager.Change.Add }.subscribe {
                linkFlowProgress(node.internals, it.logic)
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
        node.started!!.smm.changes.filter { it is StateMachineManager.Change.Add }.first().subscribe {
            linkConsensus(nodes, it.logic)
        }
    }

    private fun linkConsensus(nodes: Collection<SimulatedNode>, flow: FlowLogic<*>) {
        flow.progressTracker?.changes?.subscribe { _: ProgressTracker.Change ->
            // Runs on node thread.
            if (flow.progressTracker!!.currentStep == ProgressTracker.DONE) {
                _doneSteps.onNext(nodes)
            }
        }
    }

    val networkInitialisationFinished = allOf(*mockNet.nodes.map { it.nodeReadyFuture.toCompletableFuture() }.toTypedArray())

    fun start(): Future<Unit> {
        setCordappPackages("net.corda.irs.contract", "net.corda.finance.contract")
        mockNet.startNodes()
        // Wait for all the nodes to have finished registering with the network map service.
        return networkInitialisationFinished.thenCompose { startMainSimulation() }
    }

    /**
     * Sub-classes should override this to trigger whatever they want to simulate. This method will be invoked once the
     * network bringup has been simulated.
     */
    protected abstract fun startMainSimulation(): CompletableFuture<Unit>

    fun stop() {
        mockNet.stopNodes()
    }
}

/**
 * Helper function for verifying that a service info contains the given type of advertised service. For non-simulation cases
 * this is a configuration matter rather than implementation.
 */
fun Iterable<ServiceInfo>.containsType(type: ServiceType) = any { it.type == type }