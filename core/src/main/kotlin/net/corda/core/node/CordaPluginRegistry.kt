package net.corda.core.node

import com.esotericsoftware.kryo.Kryo

/**
 * Implement this interface on a class advertised in a META-INF/services/net.corda.core.node.CordaPluginRegistry file
 * to extend a Corda node with additional application services.
 */
abstract class CordaPluginRegistry {
    /**
     * List of JAX-RS classes inside the contract jar. They are expected to have a single parameter constructor that takes a ServiceHub as input.
     * These are listed as Class<*>, because in the future they will be instantiated inside a ClassLoader so that
     * Cordapp code can be loaded dynamically.
     */
    open val webApis: List<Class<*>> = emptyList()

    /**
     * Map of static serving endpoints to the matching resource directory. All endpoints will be prefixed with "/web" and postfixed with "\*.
     * Resource directories can be either on disk directories (especially when debugging) in the form "a/b/c". Serving from a JAR can
     *  be specified with: javaClass.getResource("<folder-in-jar>").toExternalForm()
     */
    open val staticServeDirs: Map<String, String> = emptyMap()

    /**
     * A Map with an entry for each consumed protocol used by the webAPIs.
     * The key of each map entry should contain the ProtocolLogic<T> class name.
     * The associated map values are the union of all concrete class names passed to the protocol constructor.
     * Standard java.lang.* and kotlin.* types do not need to be included explicitly.
     * This is used to extend the white listed protocols that can be initiated from the ServiceHub invokeProtocolAsync method.
     */
    open val requiredProtocols: Map<String, Set<String>> = emptyMap()

    /**
     * List of additional long lived services to be hosted within the node.
     * They are expected to have a single parameter constructor that takes a [PluginServiceHub] as input.
     * The [PluginServiceHub] will be fully constructed before the plugin service is created and will
     * allow access to the protocol factory and protocol initiation entry points there.
     */
    open val servicePlugins: List<Class<*>> = emptyList()

    /**
     * Optionally register types with [Kryo] for use over RPC, as we lock down the types that can be serialised in this
     * particular use case.
     * For example, if you add an RPC interface that carries some contract states back and forth, you need to register
     * those classes here using the [register] method on Kryo.
     *
     * TODO: Kryo and likely the requirement to register classes here will go away when we replace the serialization implementation.
     *
     * @return true if you register types, otherwise you will be filtered out of the list of plugins considered in future.
     */
    open fun registerRPCKryoTypes(kryo: Kryo): Boolean = false
}
