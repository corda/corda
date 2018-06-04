package net.corda.core.node

import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import rx.Observable

/**
 * A [CordaService] annotated class requires a constructor taking a
 * single parameter of type [AppServiceHub].
 * With the [AppServiceHub] parameter a [CordaService] is able to access to privileged operations.
 * In particular such a [CordaService] can initiate and track flows marked with [net.corda.core.flows.StartableByService].
 */
interface AppServiceHub : ServiceHub {

    /**
     * Start the given flow with the given arguments. [flow] must be annotated
     * with [net.corda.core.flows.StartableByService].
     * TODO it is assumed here that the flow object has an appropriate classloader.
     */
    fun <T> startFlow(flow: FlowLogic<T>): FlowHandle<T>

    /**
     * Start the given flow with the given arguments, returning an [Observable] with a single observation of the
     * result of running the flow. [flow] must be annotated with [net.corda.core.flows.StartableByService].
     * TODO it is assumed here that the flow object has an appropriate classloader.
     */
    fun <T> startTrackedFlow(flow: FlowLogic<T>): FlowProgressHandle<T>
}