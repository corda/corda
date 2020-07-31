package net.corda.node.services.statemachine

import java.time.Duration
import java.time.Instant

fun FlowStateMachineImpl<*>.ioRequest() = (snapshot().checkpoint.flowState as? FlowState.Started)?.flowIORequest

fun FlowStateMachineImpl<*>.ongoingDuration(now: Instant): Duration {
    return suspendedTimestamp().let { Duration.between(it, now) } ?: Duration.ZERO
}

fun FlowStateMachineImpl<*>.suspendedTimestamp() = transientState.checkpoint.timestamp

fun FlowStateMachineImpl<*>.isSuspended() = !snapshot().isFlowResumed

fun FlowStateMachineImpl<*>.isStarted() = transientState.checkpoint.flowState is FlowState.Started
