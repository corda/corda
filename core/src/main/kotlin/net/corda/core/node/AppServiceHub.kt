package net.corda.core.node

import net.corda.core.DeleteForDJVM
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.services.vault.CordaTransactionSupport
import rx.Observable

/**
 * A [net.corda.core.node.services.CordaService] annotated class requires a constructor taking a
 * single parameter of type [AppServiceHub].
 * With the [AppServiceHub] parameter a [net.corda.core.node.services.CordaService] is able to access to privileged operations.
 * In particular such a [net.corda.core.node.services.CordaService] can initiate and track flows marked
 * with [net.corda.core.flows.StartableByService].
 */
@DeleteForDJVM
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

    /**
     * Accessor to [CordaTransactionSupport] in order to perform sensitive actions within new, independent top level transaction.
     *
     * There are times when a user thread may want to perform certain actions within a new top level DB transaction. This will be an
     * independent transaction from those used in the framework.
     */
    val database: CordaTransactionSupport
}
