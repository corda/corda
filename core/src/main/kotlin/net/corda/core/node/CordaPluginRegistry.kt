package net.corda.core.node

import net.corda.core.contracts.ContractState
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.VaultQueryService
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializationCustomization
import java.util.function.Function

/**
 * Implement this interface on a class advertised in a META-INF/services/net.corda.core.node.CordaPluginRegistry file
 * to extend a Corda node with additional application services.
 */
abstract class CordaPluginRegistry {

    @Suppress("unused")
    @Deprecated("This is no longer in use, moved to WebServerPluginRegistry class in webserver module",
            level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("net.corda.webserver.services.WebServerPluginRegistry"))
    open val webApis: List<Function<CordaRPCOps, out Any>> get() = emptyList()


    @Suppress("unused")
    @Deprecated("This is no longer in use, moved to WebServerPluginRegistry class in webserver module",
            level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("net.corda.webserver.services.WebServerPluginRegistry"))
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

    /**
     * Optionally, custom schemas to be used for contract state persistence and vault custom querying
     *
     * For example, if you implement the [QueryableState] interface on a new [ContractState]
     * it needs to be registered here if you wish to perform custom queries on schema entity attributes using the
     * [VaultQueryService] API
     */
    open val requiredSchemas: Set<MappedSchema> get() = emptySet()
}