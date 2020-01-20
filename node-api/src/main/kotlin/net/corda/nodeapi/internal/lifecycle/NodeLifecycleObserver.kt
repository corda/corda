package net.corda.nodeapi.internal.lifecycle

import net.corda.core.utilities.Try

/**
 * Interface to flag interest in the Corda Node lifecycle which involves being notified when the node is starting up or
 * shutting down.
 * Unlike [net.corda.core.node.services.ServiceLifecycleObserver] this is an internal interface that provides much richer
 * functionality for interacting with node's internal services.
 */
interface NodeLifecycleObserver {

    companion object {
        const val RPC_PRIORITY_HIGH = 1200
        const val RPC_PRIORITY_NORMAL = 1100
        const val RPC_PRIORITY_LOW = 1020

        /**
         * Helper method to create a string to flag successful processing of an event.
         */
        @Suppress("unused")
        inline fun <reified T : NodeLifecycleObserver> T.reportSuccess(nodeLifecycleEvent: NodeLifecycleEvent) : String =
                "${T::class.java} successfully processed $nodeLifecycleEvent"
    }

    /**
     * Used to inform `NodeLifecycleObserver` of certain `NodeLifecycleEvent`.
     *
     * @return If even been processed successfully and the are no error conditions `Try.Success` with brief status, otherwise `Try.Failure`
     *  with exception explaining what went wrong.
     *  It is down to subject (i.e. Node) to decide what to do in case of failure and decision may depend on the Observer's priority.
     */
    fun update(nodeLifecycleEvent: NodeLifecycleEvent) : Try<String> = Try.on { "${javaClass.simpleName} ignored $nodeLifecycleEvent" }

    /**
     * It is possible to optionally override observer priority.
     *
     * `start` methods will be invoked in the ascending sequence priority order. For items with the same order alphabetical ordering
     * of full class name will be applied.
     * For `stop` methods, the order will be opposite to `start`.
     */
    val priority: Int
}

/**
 * A set of events to flag the important milestones in the lifecycle of the node.
 * @param reversedPriority flags whether it would make sense to notify observers in the reversed order.
 */
sealed class NodeLifecycleEvent(val reversedPriority: Boolean = false) {
    class BeforeNodeStart(val nodeInitialContext: NodeInitialContext) : NodeLifecycleEvent()
    class AfterNodeStart<out T : NodeServicesContext>(val nodeServicesContext: T) : NodeLifecycleEvent()
    class StateMachineStarted<out T : NodeServicesContext>(val nodeServicesContext: T) : NodeLifecycleEvent()
    class StateMachineStopped<out T : NodeServicesContext>(val nodeServicesContext: T) : NodeLifecycleEvent(reversedPriority = true)
    class BeforeNodeStop<out T : NodeServicesContext>(val nodeServicesContext: T) : NodeLifecycleEvent(reversedPriority = true)
    class AfterNodeStop(val nodeInitialContext: NodeInitialContext) : NodeLifecycleEvent(reversedPriority = true)
}