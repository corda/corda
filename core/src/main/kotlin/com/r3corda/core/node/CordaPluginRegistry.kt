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
     * Set of top level protocol class names that will be initiated by the plugin.
     * This is used to extend the white listed protocols that can be initiated from the ServiceHub invokeProtocolAsync method
     */
    val protocolLogicClassNameWhitelist: Set<String>

    /**
     * Set of associated constructor parameters that will be passed into the protocols.
     * This is used to control what can be passed to protocols initiated from the ServiceHub invokeProtocolAsync method
     */
    val protocolArgsClassNameWhitelist: Set<String>
}