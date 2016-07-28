package com.r3corda.core.node

/**
 * Implement this interface on a class advertised in a META-INF/services/com.r3corda.core.node.CordaPluginRegistry file
 * to extend a Corda node with additional application services.
 */
interface CordaPluginRegistry {
    /**
     * List of JAX-RS classes inside the contract jar. They are expected to have a single parameter constructor that takes a ServiceHub as input.
     * These are listed as Class<*>, because they will be instantiated inside an AttachmentClassLoader so that subsequent protocols, contracts, etc
     * will be running in the appropriate isolated context.
     */
    val webApis: List<Class<*>>

    /**
     * Map of static serving endpoints to the matching resource directory. All endpoints will be prefixed with "/web" and postfixed with "\*.
     * Resource directories can be either on disk directories (especially when debugging) in the form "a/b/c". Serving from a JAR can
     *  be specified with: javaClass.getResource("<folder-in-jar>").toExternalForm()
     */
    val staticServeDirs: Map<String, String>

    /**
     * A Map with an entry for each consumed protocol used by the webAPIs.
     * The key of each map entry should contain the ProtocolLogic<T> class name.
     * The associated map values are the union of all concrete class names passed to the protocol constructor.
     * Standard java.lang.* and kotlin.* types do not need to be included explicitly.
     * This is used to extend the white listed protocols that can be initiated from the ServiceHub invokeProtocolAsync method.
     */
    val requiredProtocols: Map<String, Set<String>>
}
