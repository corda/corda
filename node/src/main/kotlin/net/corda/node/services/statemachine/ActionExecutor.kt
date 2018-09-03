package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable

/**
 * An executor of a single [Action].
 */
interface ActionExecutor {
    /**
     * Execute [action] by [fiber].
     */
    @Suspendable
    fun executeAction(fiber: FlowFiber, action: Action)
}
