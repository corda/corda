package net.corda.node.utilities

import net.corda.core.flows.FlowLogic
import net.corda.core.internal.TimedFlow

/**
 * Check if a flow logic is a [TimedFlow] and if yes whether it actually can be restarted. Only flows that match both criteria should time
 * out and get restarted.
 */
internal fun FlowLogic<*>.isEnabledTimedFlow(): Boolean {
    return (this as? TimedFlow)?.isTimeoutEnabled ?: false
}