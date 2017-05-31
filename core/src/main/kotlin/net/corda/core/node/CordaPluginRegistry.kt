package net.corda.core.node

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationCustomization
import java.util.function.Function

/**
 * Implement this interface on a class advertised in a META-INF/services/net.corda.core.node.CordaPluginRegistry file
 * to extend a Corda node with additional application services.
 */
abstract class CordaPluginRegistry {
    /**
     * List of lambdas returning JAX-RS objects. They may only depend on the RPC interface, as the webserver should
     * potentially be able to live in a process separate from the node itself.
     */
    open val webApis: List<Function<CordaRPCOps, out Any>> get() = emptyList()

    /**
     * Map of static serving endpoints to the matching resource directory. All endpoints will be prefixed with "/web" and postfixed with "\*.
     * Resource directories can be either on disk directories (especially when debugging) in the form "a/b/c". Serving from a JAR can
     *  be specified with: javaClass.getResource("<folder-in-jar>").toExternalForm()
     */
    open val staticServeDirs: Map<String, String> get() = emptyMap()

    @Suppress("unused")
    @Deprecated("This is no longer needed. Instead annotate any flows that need to be invoked via RPC with " +
            "@StartableByRPC and any scheduled flows with @SchedulableFlow", level = DeprecationLevel.ERROR)
    open val requiredFlows: Map<String, Set<String>> get() = emptyMap()

    /**
     * List of lambdas constructing additional long lived services to be hosted within the node.
     * They expect a single [PluginServiceHub] parameter as input.
     * The [PluginServiceHub] will be fully constructed before the plugin service is created and will
     * allow access to the Flow factory and Flow initiation entry points there.
     */
    @Suppress("unused")
    @Deprecated("This is no longer used. If you need to create your own service, such as an oracle, then use the " +
        "@CordaService annotation. For flow registrations use @InitiatedBy.", level = DeprecationLevel.ERROR)
    open val servicePlugins: List<Function<PluginServiceHub, out Any>> get() = emptyList()

    /**
     * Optionally whitelist types for use in object serialization, as we lock down the types that can be serialized.
     *
     * For example, if you add a new [net.corda.core.contracts.ContractState] it needs to be whitelisted.  You can do that
     * either by adding the [net.corda.core.serialization.CordaSerializable] annotation or via this method.
     **
     * @return true if you register types, otherwise you will be filtered out of the list of plugins considered in future.
     */
    open fun customizeSerialization(custom: SerializationCustomization): Boolean = false
}