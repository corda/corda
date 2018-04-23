/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine.interceptors

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.statemachine.*
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * This interceptor checks whether a checkpointed fiber state can be deserialised in a separate thread.
 */
class FiberDeserializationCheckingInterceptor(
        val fiberDeserializationChecker: FiberDeserializationChecker,
        val delegate: TransitionExecutor
) : TransitionExecutor {
    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {
        val (continuation, nextState) = delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)
        val previousFlowState = previousState.checkpoint.flowState
        val nextFlowState = nextState.checkpoint.flowState
        if (nextFlowState is FlowState.Started) {
            if (previousFlowState !is FlowState.Started || previousFlowState.frozenFiber != nextFlowState.frozenFiber) {
                fiberDeserializationChecker.submitCheck(nextFlowState.frozenFiber)
            }
        }
        return Pair(continuation, nextState)
    }
}

/**
 * A fiber deserialisation checker thread. It checks the queued up serialised checkpoints to see if they can be
 * deserialised. This is only run in development mode to allow detecting of corrupt serialised checkpoints before they
 * are actually used.
 */
class FiberDeserializationChecker {
    companion object {
        val log = contextLogger()
    }

    private sealed class Job {
        class Check(val serializedFiber: SerializedBytes<FlowStateMachineImpl<*>>) : Job()
        object Finish : Job()
    }

    private var checkerThread: Thread? = null
    private val jobQueue = LinkedBlockingQueue<Job>()
    private var foundUnrestorableFibers: Boolean = false

    fun start(checkpointSerializationContext: SerializationContext) {
        require(checkerThread == null)
        checkerThread = thread(name = "FiberDeserializationChecker") {
            while (true) {
                val job = jobQueue.take()
                when (job) {
                    is Job.Check -> {
                        try {
                            job.serializedFiber.deserialize(context = checkpointSerializationContext)
                        } catch (throwable: Throwable) {
                            log.error("Encountered unrestorable checkpoint!", throwable)
                            foundUnrestorableFibers = true
                        }
                    }
                    Job.Finish -> {
                        return@thread
                    }
                }
            }
        }
    }

    fun submitCheck(serializedFiber: SerializedBytes<FlowStateMachineImpl<*>>) {
        jobQueue.add(Job.Check(serializedFiber))
    }

    /**
     * Returns true if some unrestorable checkpoints were encountered, false otherwise
     */
    fun stop(): Boolean {
        jobQueue.add(Job.Finish)
        checkerThread?.join()
        return foundUnrestorableFibers
    }
}
