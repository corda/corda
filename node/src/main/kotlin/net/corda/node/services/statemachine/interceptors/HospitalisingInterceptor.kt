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
import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.*
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult
import java.util.concurrent.ConcurrentHashMap

/**
 * This interceptor notifies the passed in [flowHospital] in case a flow went through a clean->errored or a errored->clean
 * transition.
 */
class HospitalisingInterceptor(
        private val flowHospital: FlowHospital,
        private val delegate: TransitionExecutor
) : TransitionExecutor {
    private val hospitalisedFlows = ConcurrentHashMap<StateMachineRunId, FlowFiber>()

    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {
        val (continuation, nextState) = delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)
        when (nextState.checkpoint.errorState) {
            ErrorState.Clean -> {
                if (hospitalisedFlows.remove(fiber.id) != null) {
                    flowHospital.flowCleaned(fiber)
                }
            }
            is ErrorState.Errored -> {
                if (hospitalisedFlows.putIfAbsent(fiber.id, fiber) == null) {
                    flowHospital.flowErrored(fiber)
                }
            }
        }
        if (nextState.isRemoved) {
            hospitalisedFlows.remove(fiber.id)
        }
        return Pair(continuation, nextState)
    }
}