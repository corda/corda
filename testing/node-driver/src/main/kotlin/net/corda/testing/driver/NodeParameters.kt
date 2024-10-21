package net.corda.testing.driver

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User

/**
 * Parameters for creating a node for [DriverDSL.startNode].
 *
 * @property providedName Optional name of the node, which will be its legal name in [Party]. Defaults to something
 *     random. Note that this must be unique as the driver uses it as a primary key!
 * @property rpcUsers List of users who are authorised to use the RPC system. Defaults to a single user with
 *     all permissions.
 * @property verifierType The type of transaction verifier to use. See: [VerifierType]
 * @property customOverrides A map of custom node configuration overrides.
 * @property startInSameProcess Determines if the node should be started inside the same process the Driver is running
 *     in. If null the Driver-level value will be used.
 * @property maximumHeapSize The maximum JVM heap size to use for the node. Defaults to 512 MB.
 * @property additionalCordapps Additional [TestCordapp]s that this node will have available, in addition to the ones common to all nodes
 * managed by the [DriverDSL].
 * @property logLevelOverride log level to be passed as parameter to an out of process node. ERROR, WARN, INFO, DEBUG, TRACE. This overrides debug port
 * log level argument.
 * @property rpcAddress optional override for RPC address on which node will be accepting RPC connections from the clients. Port provided must be vacant.
 */
@Suppress("unused", "TooManyFunctions")
data class NodeParameters(
        val providedName: CordaX500Name? = null,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val customOverrides: Map<String, Any?> = emptyMap(),
        val startInSameProcess: Boolean? = null,
        val maximumHeapSize: String = System.getenv("DRIVER_NODE_MEMORY") ?: "512m",
        val additionalCordapps: Collection<TestCordapp> = emptySet(),
        val flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>> = emptyMap(),
        val logLevelOverride: String? = null,
        val rpcAddress: NetworkHostAndPort? = null,
        val systemProperties: Map<String, String> = emptyMap(),
        val legacyContracts: Collection<TestCordapp> = emptySet()
) {
    /**
     * Create a new node parameters object with default values. Each parameter can be specified with its wither method which returns a copy
     * with that value.
     */
    constructor() : this(providedName = null)

    fun withProvidedName(providedName: CordaX500Name?): NodeParameters = copy(providedName = providedName)
    fun withRpcUsers(rpcUsers: List<User>): NodeParameters = copy(rpcUsers = rpcUsers)
    fun withVerifierType(verifierType: VerifierType): NodeParameters = copy(verifierType = verifierType)
    fun withCustomOverrides(customOverrides: Map<String, Any?>): NodeParameters = copy(customOverrides = customOverrides)
    fun withStartInSameProcess(startInSameProcess: Boolean?): NodeParameters = copy(startInSameProcess = startInSameProcess)
    fun withMaximumHeapSize(maximumHeapSize: String): NodeParameters = copy(maximumHeapSize = maximumHeapSize)
    fun withAdditionalCordapps(additionalCordapps: Set<TestCordapp>): NodeParameters = copy(additionalCordapps = additionalCordapps)
    fun withFlowOverrides(flowOverrides: Map<Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>): NodeParameters = copy(flowOverrides = flowOverrides)
    fun withLogLevelOverride(logLevelOverride: String?): NodeParameters = copy(logLevelOverride = logLevelOverride)
    fun withRpcAddress(rpcAddress: NetworkHostAndPort?): NodeParameters = copy(rpcAddress = rpcAddress)
    fun withSystemProperties(systemProperties: Map<String, String>): NodeParameters = copy(systemProperties = systemProperties)
    fun withLegacyContracts(legacyContracts: Collection<TestCordapp>): NodeParameters = copy(legacyContracts = legacyContracts)

    constructor(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String
    ) : this(
            providedName,
            rpcUsers,
            verifierType,
            customOverrides,
            startInSameProcess,
            maximumHeapSize,
            additionalCordapps = emptySet())

    fun copy(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String
    ) = this.copy(
            providedName = providedName,
            rpcUsers = rpcUsers,
            verifierType = verifierType,
            customOverrides = customOverrides,
            startInSameProcess = startInSameProcess,
            maximumHeapSize = maximumHeapSize,
            additionalCordapps = additionalCordapps
    )

    constructor(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>
    ) : this(
            providedName,
            rpcUsers,
            verifierType,
            customOverrides,
            startInSameProcess,
            maximumHeapSize,
            additionalCordapps,
            flowOverrides,
            logLevelOverride = null)

    fun copy(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>
    ) = this.copy(
            providedName = providedName,
            rpcUsers = rpcUsers,
            verifierType = verifierType,
            customOverrides = customOverrides,
            startInSameProcess = startInSameProcess,
            maximumHeapSize = maximumHeapSize,
            additionalCordapps = additionalCordapps,
            flowOverrides = flowOverrides,
            logLevelOverride = logLevelOverride)

    constructor(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null
    ) : this(
            providedName,
            rpcUsers,
            verifierType,
            customOverrides,
            startInSameProcess,
            maximumHeapSize,
            additionalCordapps,
            flowOverrides,
            logLevelOverride,
            rpcAddress = null)

    @Suppress("LongParameterList")
    fun copy(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null
    ) = this.copy(
            providedName = providedName,
            rpcUsers = rpcUsers,
            verifierType = verifierType,
            customOverrides = customOverrides,
            startInSameProcess = startInSameProcess,
            maximumHeapSize = maximumHeapSize,
            additionalCordapps = additionalCordapps,
            flowOverrides = flowOverrides,
            logLevelOverride = logLevelOverride,
            rpcAddress = rpcAddress)

    constructor(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null,
            rpcAddress: NetworkHostAndPort? = null
    ) : this(
            providedName,
            rpcUsers,
            verifierType,
            customOverrides,
            startInSameProcess,
            maximumHeapSize,
            additionalCordapps,
            flowOverrides,
            logLevelOverride,
            rpcAddress,
            systemProperties = emptyMap())

    @Suppress("LongParameterList")
    fun copy(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null,
            rpcAddress: NetworkHostAndPort? = null
    ) = this.copy(
            providedName = providedName,
            rpcUsers = rpcUsers,
            verifierType = verifierType,
            customOverrides = customOverrides,
            startInSameProcess = startInSameProcess,
            maximumHeapSize = maximumHeapSize,
            additionalCordapps = additionalCordapps,
            flowOverrides = flowOverrides,
            logLevelOverride = logLevelOverride,
            rpcAddress = rpcAddress,
            systemProperties = systemProperties)

    constructor(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null,
            rpcAddress: NetworkHostAndPort? = null,
            systemProperties: Map<String, String> = emptyMap()
    ) : this(
            providedName,
            rpcUsers,
            verifierType,
            customOverrides,
            startInSameProcess,
            maximumHeapSize,
            additionalCordapps,
            flowOverrides,
            logLevelOverride,
            rpcAddress,
            systemProperties,
            legacyContracts = emptySet())

    @Suppress("LongParameterList")
    fun copy(
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String,
            additionalCordapps: Collection<TestCordapp> = emptySet(),
            flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>>,
            logLevelOverride: String? = null,
            rpcAddress: NetworkHostAndPort? = null,
            systemProperties: Map<String, String> = emptyMap()
    ) = this.copy(
            providedName = providedName,
            rpcUsers = rpcUsers,
            verifierType = verifierType,
            customOverrides = customOverrides,
            startInSameProcess = startInSameProcess,
            maximumHeapSize = maximumHeapSize,
            additionalCordapps = additionalCordapps,
            flowOverrides = flowOverrides,
            logLevelOverride = logLevelOverride,
            rpcAddress = rpcAddress,
            systemProperties = systemProperties,
            legacyContracts = legacyContracts)

}
