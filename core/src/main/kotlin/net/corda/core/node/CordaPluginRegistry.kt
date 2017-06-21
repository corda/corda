package net.corda.core.node

import net.corda.core.messaging.CordaRPCOps
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
     * Optionally whitelist types for use in object serialization, as we lock down the types that can be serialized.
     *
     * For example, if you add a new [net.corda.core.contracts.ContractState] it needs to be whitelisted.  You can do that
     * either by adding the [net.corda.core.serialization.CordaSerializable] annotation or via this method.
     **
     * @return true if you register types, otherwise you will be filtered out of the list of plugins considered in future.
     */
    open fun customizeSerialization(custom: SerializationCustomization): Boolean = false
}