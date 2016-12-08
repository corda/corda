package net.corda.node.utilities

import net.corda.core.ThreadBox
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.statemachine.StateMachineManager
import java.util.*

/**
 * This observes the [StateMachineManager] and follows the progress of [FlowLogic]s until they complete in the order
 * they are added to the [StateMachineManager].
 */
class ANSIProgressObserver(val smm: StateMachineManager) {
    init {
        smm.changes.subscribe { change ->
            when (change.addOrRemove) {
                AddOrRemove.ADD -> addFlowLogic(change.logic)
                AddOrRemove.REMOVE -> removeFlowLogic(change.logic)
            }
        }
    }

    private class Content {
        var currentlyRendering: FlowLogic<*>? = null
        val pending = ArrayDeque<FlowLogic<*>>()
    }

    private val state = ThreadBox(Content())

    private fun wireUpProgressRendering() {
        state.locked {
            // Repeat if the progress of the ones we pop from the queue are already done
            do {
                currentlyRendering = pending.poll()
                if (currentlyRendering?.progressTracker != null) {
                    ANSIProgressRenderer.progressTracker = currentlyRendering!!.progressTracker
                }
            } while (currentlyRendering?.progressTracker?.currentStep == ProgressTracker.DONE)
        }
    }

    private fun removeFlowLogic(flowLogic: FlowLogic<*>) {
        state.locked {
            flowLogic.progressTracker?.currentStep = ProgressTracker.DONE
            if (currentlyRendering == flowLogic) {
                wireUpProgressRendering()
            }
        }
    }

    private fun addFlowLogic(flowLogic: FlowLogic<*>) {
        state.locked {
            pending.add(flowLogic)
            if ((currentlyRendering?.progressTracker?.currentStep ?: ProgressTracker.DONE) == ProgressTracker.DONE) {
                wireUpProgressRendering()
            }
        }
    }
}
