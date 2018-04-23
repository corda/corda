/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.*
import net.corda.node.services.statemachine.*
import java.security.SecureRandom

/**
 * Specifies what strategy to use to persist received messages.
 *
 * - [OnDeliver] means the received message should be persisted in a checkpoint as soon as possible. This means that the
 *     next time the flow enters the state machine a checkpoint will be created with the current state and the received
 *     message. Note that the deduplication ID of the received message will be committed together with the checkpoint.
 *     This means that for each [FlowSession.receive] *two* checkpoints will be created, one when receive() is called,
 *     and one when the message is received. It also means that internal session messages not exposed to the flow also
 *     create checkpoints.
 * - [OnNextCommit] means that instead of creating an explicit checkpoint we wait for the next one that would happen
 *     anyway. During this time the message will not be acknowledged.
 *     Note that this also means that if the flow is completely idempotent then the message will never be persisted as
 *     no checkpoints are ever committed (unless the flow errors). In this case the message will be acknowledged at the
 *     very end of the flow.
 *     In general turning this on is safe and much more efficient than [OnDeliver]. However if the flow is hogging the
 *     fiber (for example doing some IO) then the acknowledgement window of the received message will be extended to
 *     an arbitrary length.
 */
enum class SessionDeliverPersistenceStrategy {
    OnDeliver,
    OnNextCommit
}

/**
 * @property sessionDeliverPersistenceStrategy see [SessionDeliverPersistenceStrategy]
 * @property eventQueueSize the size of a flow's event queue. If the queue gets full the thread scheduling the event
 *     will block. An example scenario would be if the flow is waiting for a lot of messages at once, but is slow at
 *     processing each.
 */
data class StateMachineConfiguration(
        val sessionDeliverPersistenceStrategy: SessionDeliverPersistenceStrategy,
        val eventQueueSize: Int
) {
    companion object {
        val default = StateMachineConfiguration(
                sessionDeliverPersistenceStrategy = SessionDeliverPersistenceStrategy.OnDeliver,
                eventQueueSize = 16
        )
    }
}

class StateMachine(
        val id: StateMachineRunId,
        val configuration: StateMachineConfiguration,
        val secureRandom: SecureRandom
) {
    fun transition(event: Event, state: StateMachineState): TransitionResult {
        return TopLevelTransition(TransitionContext(id, configuration, secureRandom), state, event).transition()
    }
}
