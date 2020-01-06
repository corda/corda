package net.corda.ext.api.rpc

import net.corda.core.messaging.RPCOps
import net.corda.ext.api.NodeInitialContext

/**
 * Defines an interface for `RPCOps` that can be discovered during startup.
 * The idea is that there can be multiple implementations of the same RPC interface in the classpath. Upon start-up the process should be able
 * to select desired implementation of RPC interface, selection will be done using maximum possible version out of all discovered implementations.
 */
interface PluggableRPCOps<T : RPCOps> : RPCOps {
    /**
     * Returns the version of the implementation, may also throw a runtime exception if implementation of `PluggableRPCOps` cannot work with
     * given `NodeInitialContext`.
     * In this case this implementation should be treated as if it does not exist.
     */
    fun getVersion(nodeServicesContext: NodeInitialContext) : Int

    /**
     * Specifies the main interface which plugin implements and to which versioning applies.
     */
    val targetInterface: Class<T>
}
