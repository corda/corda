package net.corda.node.services.statemachine.hospital.external

import net.corda.core.internal.TimedFlow
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.FlowTimeoutException
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState

// [DoctorTimeout] could go in [FlowTimeoutException]? [FlowTimeoutException] and [DoctorTimeout] could be moved in IdempotentFlow.kt if we move StaffedFlowHospital.kt in :core?

/**
 * Restarts [TimedFlow], keeping track of the number of retries and making sure it does not
 * exceed the limit specified by the [FlowTimeoutException].
 */
object DoctorTimeout : StaffedFlowHospital.Staff {
    override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: StaffedFlowHospital.FlowMedicalHistory): StaffedFlowHospital.Diagnosis {
        if (newError is FlowTimeoutException) {
            return StaffedFlowHospital.Diagnosis.DISCHARGE
        }
        return StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
    }
}