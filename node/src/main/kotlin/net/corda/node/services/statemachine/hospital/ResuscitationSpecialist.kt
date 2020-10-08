package net.corda.node.services.statemachine.hospital

import net.corda.node.services.statemachine.ErrorStateTransitionException
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StaffedFlowHospital.Diagnosis
import net.corda.node.services.statemachine.StateMachineState

/**
 * Handles errors coming from the processing of errors events ([Event.StartErrorPropagation] and [Event.RetryFlowFromSafePoint]),
 * returning a [Diagnosis.RESUSCITATE] diagnosis
 */
object ResuscitationSpecialist : StaffedFlowHospital.Staff {
    override fun consult(
            flowFiber: FlowFiber,
            currentState: StateMachineState,
            newError: Throwable,
            history: StaffedFlowHospital.FlowMedicalHistory
    ): Diagnosis {
        return if (newError is ErrorStateTransitionException) {
            Diagnosis.RESUSCITATE
        } else {
            Diagnosis.NOT_MY_SPECIALTY
        }
    }
}