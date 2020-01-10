package net.corda.core.node.services

import java.lang.Exception

/**
 * Specifies that given [CordaService] is interested to know about important milestones of Corda Node lifecycle and potentially react to them.
 */
interface NodeLifecycleObserverService {

    /**
     * This is called when Corda Node is fully started such that [net.corda.core.node.AppServiceHub] available for [CordaService] to be use.
     * Default implementation does nothing.
     *
     * @throws [CordaServiceCriticalFailureException] if upon processing lifecycle event critical failure has occurred and it will not make
     *      sense for Corda node to continue its operation. The caller of [onNodeStarted] method will endeavor to terminate node's JVM as soon
     *      as practically possible.
     */
    @Throws(CordaServiceCriticalFailureException::class)
    fun onNodeStarted() {}

    /**
     * Notification to inform that Corda Node is shutting down. In response to this event [CordaService] may perform clean-up of some critical
     * resources.
     * Default implementation does nothing.
     */
    fun onNodeShuttingDown() {}
}

class CordaServiceCriticalFailureException(message : String, cause: Throwable?) : Exception(message, cause) {
    constructor(message : String) : this(message, null)
}