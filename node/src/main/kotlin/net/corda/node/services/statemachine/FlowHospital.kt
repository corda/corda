/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

/**
 * A flow hospital is a class that is notified when a flow transitions into an error state due to an uncaught exception
 * or internal error condition, and when it becomes clean again (e.g. due to a resume).
 * Also see [net.corda.node.services.statemachine.interceptors.HospitalisingInterceptor].
 */
interface FlowHospital {
    /**
     * The flow running in [flowFiber] has errored.
     */
    fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, errors: List<Throwable>)

    /**
     * The flow running in [flowFiber] has cleaned, possibly as a result of a flow hospital resume.
     */
    fun flowCleaned(flowFiber: FlowFiber)

    /**
     * The flow has been removed from the state machine.
     */
    fun flowRemoved(flowFiber: FlowFiber)
}
