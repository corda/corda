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
    fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable)

    /**
     * The flow running in [flowFiber] has cleaned, possibly as a result of a flow hospital resume.
     */
    fun flowCleaned(flowFiber: FlowFiber)

    /**
     * The flow has been removed from the state machine.
     */
    fun flowRemoved(flowFiber: FlowFiber)
}
