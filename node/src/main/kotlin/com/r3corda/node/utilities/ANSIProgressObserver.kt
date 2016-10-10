package com.r3corda.node.utilities

import com.r3corda.core.ThreadBox
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.node.services.statemachine.StateMachineManager
import java.util.*

/**
 * This observes the [StateMachineManager] and follows the progress of [ProtocolLogic]s until they complete in the order
 * they are added to the [StateMachineManager].
 */
class ANSIProgressObserver(val smm: StateMachineManager) {
    init {
        smm.changes.subscribe { change ->
            when (change.addOrRemove) {
                AddOrRemove.ADD -> addProtocolLogic(change.logic)
                AddOrRemove.REMOVE -> removeProtocolLogic(change.logic)
            }
        }
    }

    private class Content {
        var currentlyRendering: ProtocolLogic<*>? = null
        val pending = ArrayDeque<ProtocolLogic<*>>()
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

    private fun removeProtocolLogic(protocolLogic: ProtocolLogic<*>) {
        state.locked {
            protocolLogic.progressTracker?.currentStep = ProgressTracker.DONE
            if (currentlyRendering == protocolLogic) {
                wireUpProgressRendering()
            }
        }
    }

    private fun addProtocolLogic(protocolLogic: ProtocolLogic<*>) {
        state.locked {
            pending.add(protocolLogic)
            if ((currentlyRendering?.progressTracker?.currentStep ?: ProgressTracker.DONE) == ProgressTracker.DONE) {
                wireUpProgressRendering()
            }
        }
    }
}
