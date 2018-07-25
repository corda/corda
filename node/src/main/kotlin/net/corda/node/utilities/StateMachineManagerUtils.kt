package net.corda.node.utilities

import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.statemachine.StateMachineManager
import java.lang.reflect.Field

/**
 * The flow de-serialized from the checkpoint will contain a new instance of the progress tracker, which means that
 * any existing flow observers would be lost. We need to replace it with the old progress tracker to ensure progress
 * updates are correctly sent out after the flow is retried.
 *
 * If the new tracker contains any child trackers from sub-flows, we need to attach those to the old tracker as well.
 */
//TODO: instead of replacing the progress tracker after constructing the flow logic, we should inject it during fiber deserialization
fun StateMachineManager.injectOldProgressTracker(oldTracker: ProgressTracker?, newFlowLogic: FlowLogic<*>) {
    if (oldTracker != null) {
        val newTracker = newFlowLogic.progressTracker
        if (newTracker != null) {
            attachNewChildren(oldTracker, newTracker)
            replaceTracker(newFlowLogic, oldTracker)
        }
    }
}

private fun attachNewChildren(oldTracker: ProgressTracker, newTracker: ProgressTracker) {
    oldTracker.currentStep = newTracker.currentStep
    oldTracker.steps.forEachIndexed { index, step ->
        val newStep = newTracker.steps[index]
        val newChildTracker = newTracker.getChildProgressTracker(newStep)
        newChildTracker?.let { child ->
            oldTracker.setChildProgressTracker(step, child)
        }
    }
    resubscribeToChildren(oldTracker)
}

/**
 * Re-subscribes to child tracker observables. When a nested progress tracker is deserialized from a checkpoint,
 * it retains the child links, but does not automatically re-subscribe to the child changes.
 */
private fun resubscribeToChildren(tracker: ProgressTracker) {
    tracker.steps.forEach {
        val childTracker = tracker.getChildProgressTracker(it)
        if (childTracker != null) {
            tracker.setChildProgressTracker(it, childTracker)
            resubscribeToChildren(childTracker)
        }
    }
}

/** Replaces the deserialized [ProgressTracker] in the [newFlowLogic] with the old one to retain old subscribers. */
private fun replaceTracker(newFlowLogic: FlowLogic<*>, oldProgressTracker: ProgressTracker?) {
    val field = getProgressTrackerField(newFlowLogic)
    field?.apply {
        isAccessible = true
        set(newFlowLogic, oldProgressTracker)
    }
}

private fun getProgressTrackerField(newFlowLogic: FlowLogic<*>): Field? {
    var clazz: Class<*> = newFlowLogic::class.java
    var field: Field? = null
    // The progress tracker field may have been overridden in an abstract superclass, so we have to traverse up
    // the hierarchy.
    while (clazz != FlowLogic::class.java) {
        field = clazz.declaredFields.firstOrNull { it.name == "progressTracker" }
        if (field == null) {
            clazz = clazz.superclass
        } else {
            break
        }
    }
    return field
}