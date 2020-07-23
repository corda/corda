package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.div
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.NotaryHandle
import net.corda.testing.driver.ReusableDriverDsl
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.driver.internal.NodeHandleInternal
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

class ReusableDriver private constructor(private val driver: InternalDriverDSL) : InternalDriverDSL, ReusableDriverDsl, AutoCloseable {
    companion object {
        private val drivers = mutableMapOf<DriverParameters, ReusableDriver>()

        private val shutdownHook = addShutdownHook { closeAll() }

        private var serializationEnabler: AutoCloseable? = null

        private val startTime = Instant.now()

        internal val log = contextLogger()

        private fun createDriver(parameters: DriverParameters): ReusableDriver {
            val driver = DriverDSLImpl(
                    portAllocation = parameters.portAllocation,
                    debugPortAllocation = parameters.debugPortAllocation,
                    systemProperties = parameters.systemProperties,
                    driverDirectory = parameters.driverDirectory.toAbsolutePath(),
                    useTestClock = parameters.useTestClock,
                    isDebug = parameters.isDebug,
                    startNodesInProcess = parameters.startNodesInProcess,
                    waitForAllNodesToFinish = parameters.waitForAllNodesToFinish,
                    extraCordappPackagesToScan = @Suppress("DEPRECATION") parameters.extraCordappPackagesToScan,
                    notarySpecs = parameters.notarySpecs,
                    jmxPolicy = parameters.jmxPolicy,
                    compatibilityZone = null,
                    networkParameters = parameters.networkParameters,
                    notaryCustomOverrides = parameters.notaryCustomOverrides,
                    inMemoryDB = parameters.inMemoryDB,
                    cordappsForAllNodes = uncheckedCast(parameters.cordappsForAllNodes),
                    djvmBootstrapSource = parameters.djvmBootstrapSource,
                    djvmCordaSource = parameters.djvmCordaSource,
                    environmentVariables = parameters.environmentVariables)
            if (serializationEnabler == null) {
                serializationEnabler = setDriverSerialization(driver.cordappsClassLoader)
            }
            driver.start()
            return ReusableDriver(driver)
        }

        private fun closeAll() {
            shutdownHook.cancel()
            drivers.values.forEach { it.shutdown() }
            drivers.clear()
            serializationEnabler?.close()
        }

        operator fun invoke(parameters: DriverParameters): ReusableDriver {
            val parametersKey = parameters
                    .withDriverDirectory(Paths.get("build") / "node-driver" / "reusable-driver")
                    .withNetworkParameters(parameters.networkParameters.copy(modifiedTime = startTime))

            return drivers.compute(parametersKey) { _, driver ->
                driver?.recover() ?: createDriver(parameters)
            }!!
        }
    }

    private fun recover(): ReusableDriver? {
        return try {
            recoveries.forEach { it.invoke() }
            recoveries.clear()
            this
        } catch (e: Exception) {
            log.warn("Can not recover driver, will use a new one", e)
            shutdown()
            null
        }
    }

    override fun close() {
        startedNodes.forEach { (parameter, nodeFutures) ->
            nodeFutures.forEach { nodeFuture ->
                if (nodeFuture.isDone) {
                    val node = nodeFuture.get()
                    if ((node is NodeHandleInternal) && (node.running())) {
                        reusableNodes.getOrPut(parameter, { mutableListOf() })
                                .add(node)
                    }
                } else {
                    nodeFuture.then { it.get()?.stop() }
                }
            }
        }
        startedNodes.clear()
    }

    private val startedNodes = mutableMapOf<NodeKey, MutableList<CordaFuture<NodeHandle>>>()
    private val reusableNodes = mutableMapOf<NodeKey, MutableList<NodeHandleInternal>>()
    private val runningNamedDriver = mutableMapOf<CordaX500Name, NodeKey>()

    override val shutdownManager: ShutdownManager
        get() = driver.shutdownManager
    override val cordappsClassLoader: ClassLoader?
        get() = driver.cordappsClassLoader

    override fun baseDirectory(nodeName: CordaX500Name): Path =
            driver.baseDirectory(nodeName)

    override fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration, warnCount: Int, check: () -> A?): CordaFuture<A> =
            driver.pollUntilNonNull(pollName, pollInterval, warnCount, check)

    override fun start() =
            driver.start()

    override fun shutdown() =
            driver.shutdown()

    override fun startNode(parameters: NodeParameters, bytemanPort: Int?): CordaFuture<NodeHandle> {
        val key = NodeKey(parameters, bytemanPort)
        if (parameters.providedName != null) {
            val runningKey = runningNamedDriver[parameters.providedName]
            if ((runningKey != null) && (runningKey != key)) {
                val runningNodes = reusableNodes.remove(runningKey)
                runningNodes?.forEach { it.stop() }
            }
            runningNamedDriver[parameters.providedName] = key
        }
        val reusableNodes = reusableNodes.getOrDefault(key, mutableListOf())
        val size = reusableNodes.size
        val nodeFuture = if (size > 0) {
            log.debug("Reusing node $parameters")
            val node = reusableNodes.removeAt(size - 1)
            doneFuture(node as NodeHandle)
        } else {
            log.debug("Creating node $parameters")
            val node = driver.startNode(parameters, bytemanPort)
            node
        }
        startedNodes.getOrPut(key, { mutableListOf() })
                .add(nodeFuture)
        return nodeFuture
    }

    override fun startNode(parameters: NodeParameters): CordaFuture<NodeHandle> = startNode(parameters, bytemanPort = null)

    private val recoveries = mutableListOf<() -> Unit>()

    override fun recoverBy(recovery: () -> Unit) {
        recoveries.add(recovery)
    }

    override val notaryHandles: List<NotaryHandle>
        get() = driver.notaryHandles

    override fun startWebserver(handle: NodeHandle, maximumHeapSize: String): CordaFuture<WebserverHandle> =
            driver.startWebserver(handle, maximumHeapSize)

    private data class NodeKey(val parameters: NodeParameters, val bytemanPort: Int?)
}