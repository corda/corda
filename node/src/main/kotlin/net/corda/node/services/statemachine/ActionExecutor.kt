package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import java.sql.SQLException

/**
 * An executor of a single [Action].
 */
interface ActionExecutor {
    /**
     * Execute [action] by [fiber].
     */
    @Suspendable
    @Throws(SQLException::class)
    fun executeAction(fiber: FlowFiber, action: Action)
}
