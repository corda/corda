package net.corda.ext.api.lifecycle

import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.Try
import net.corda.ext.api.NodeInitialContext
import net.corda.ext.api.NodeServicesContext
import net.corda.ext.api.rpc.PluggableRPCOps

/**
 * Interface to flag interest in the Corda Node lifecycle which involves being notified when the node is starting up or
 * shutting down.
 */
interface NodeLifecycleObserver {

    companion object {
        const val PRIORITY_HIGH = 20
        const val PRIORITY_NORMAL = 100
        const val PRIORITY_LOW = 200

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
     * It is possibly to optionally override observer priority.
     *
     * `start` methods will be invoked in the ascending sequence priority order. For items with the same order alphabetical ordering
     * of full class name will be applied.
     * For `stop` methods, the order will be opposite to `start`.
     */
    val priority: Int
        get() {
            return PRIORITY_NORMAL
        }
}

/**
 * A set of events to flag the important milestones in the lifecycle of the node.
 * @param shutdown flags whether this is a shutdown event, i.e. it would make sense to notify observers in the reversed order.
 */
sealed class NodeLifecycleEvent(val shutdown: Boolean = false) {
    class BeforeStart(val nodeInitialContext: NodeInitialContext) : NodeLifecycleEvent()
    class AfterStart(val nodeServicesContext: NodeServicesContext) : NodeLifecycleEvent()
    class BeforeStop(val nodeServicesContext: NodeServicesContext) : NodeLifecycleEvent(shutdown = true)
    class AfterStop(val nodeInitialContext: NodeInitialContext) : NodeLifecycleEvent(shutdown = true)
}

/**
 * Combined interface of `PluggableRPCOps` and `NodeLifecycleObserver` which should help implementations to manage critical
 * resources as necessary.
 */
interface NodeRpcOps<T : RPCOps> : NodeLifecycleObserver, PluggableRPCOps<T>