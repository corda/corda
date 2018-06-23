package net.corda.node.utilities

import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.statemachine.StateMachineManagerInternal

/**
 * The flow de-serialized from the checkpoint will contain a new instance of the progress tracker, which means that
 * any existing flow observers would be lost. We need to replace it with the old progress tracker to ensure progress
 * updates are correctly sent out after the flow is retried.
 */
fun StateMachineManagerInternal.injectOldProgressTracker(oldProgressTracker: ProgressTracker?, newFlowLogic: FlowLogic<*>) {
    if (oldProgressTracker != null) {
        try {
            val field = newFlowLogic::class.java.getDeclaredField("progressTracker")
            field.isAccessible = true
            field.set(newFlowLogic, oldProgressTracker)
        } catch (e: NoSuchFieldException) {
            // The flow does not use a progress tracker.
        }
    }
}