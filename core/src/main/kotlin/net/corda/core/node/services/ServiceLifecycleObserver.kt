package net.corda.core.node.services

import java.lang.Exception

/**
 * Specifies that given [CordaService] is interested to know about important milestones of Corda Node lifecycle and potentially react to them.
 * Subscription can be performed using [net.corda.core.node.AppServiceHub.register] method from a constructor of [CordaService].
 */
@FunctionalInterface
interface ServiceLifecycleObserver {
    /**
     * A handler for [ServiceLifecycleEvent]s.
     * Default implementation does nothing.
     */
    @Throws(CordaServiceCriticalFailureException::class)
    fun onServiceLifecycleEvent(event: ServiceLifecycleEvent)
}

enum class ServiceLifecycleEvent {
    /**
     * This event is dispatched when CorDapp is fully started such that [net.corda.core.node.AppServiceHub] available
     * for [CordaService] to be use.
     *
     * If a handler for this event throws [CordaServiceCriticalFailureException] - this is the way to flag that it will not make
     * sense for Corda node to continue its operation. The lifecycle events dispatcher will endeavor to terminate node's JVM as soon
     * as practically possible.
     */
    CORDAPP_STARTED,

    /**
     * Notification to inform that CorDapp is shutting down. In response to this event [CordaService] may perform clean-up of some critical
     * resources.
     * This type of event is dispatched on best effort basis, i.e. if there was a failure during [CORDAPP_STARTED] processing this event may
     * not be dispatched.
     */
    CORDAPP_STOPPED
}

/**
 * Please see [ServiceLifecycleEvent.CORDAPP_STARTED] for the purpose of this exception.
 */
class CordaServiceCriticalFailureException(message : String, cause: Throwable?) : Exception(message, cause) {
    constructor(message : String) : this(message, null)
}