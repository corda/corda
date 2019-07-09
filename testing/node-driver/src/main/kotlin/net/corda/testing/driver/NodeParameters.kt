package net.corda.testing.driver

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
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
 */
@Suppress("unused")
data class NodeParameters(
        val providedName: CordaX500Name? = null,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val customOverrides: Map<String, Any?> = emptyMap(),
        val startInSameProcess: Boolean? = null,
        val maximumHeapSize: String = System.getenv("DRIVER_NODE_MEMORY") ?: "512m",
        val additionalCordapps: Collection<TestCordapp> = emptySet(),
        val flowOverrides: Map<out Class<out FlowLogic<*>>, Class<out FlowLogic<*>>> = emptyMap(),
        val logLevelOverride : String? = null
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
    fun withLogLevelOverride(logLevelOverride : String?) : NodeParameters = copy(logLevelOverride = logLevelOverride)

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

}
