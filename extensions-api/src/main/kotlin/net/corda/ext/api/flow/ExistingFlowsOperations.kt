package net.corda.ext.api.flow

import net.corda.core.CordaInternal
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.DataFeed

/**
 * Defines a set of operations that can be performed on the currently running flow.
 */
@CordaInternal
interface ExistingFlowsOperations {

    /**
     * Returns the list of live state machines and a stream of subsequent additions/removals of them.
     */
    fun track(): DataFeed<List<FlowLogic<*>>, out Change>

    /**
     * Attempts to kill a flow. This is not a clean termination and should be reserved for exceptional cases such as stuck fibers.
     *
     * @return whether the flow existed and was killed.
     */
    fun killFlow(id: StateMachineRunId): Boolean
}