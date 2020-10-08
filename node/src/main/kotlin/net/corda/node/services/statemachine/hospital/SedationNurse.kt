package net.corda.node.services.statemachine.hospital

import net.corda.core.flows.HospitalizeFlowException
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.mentionsThrowable

/**
 * Keeps the flow in for overnight observation if [HospitalizeFlowException] is received.
 */
object SedationNurse : StaffedFlowHospital.Staff {
    override fun consult(
            flowFiber: FlowFiber,
            currentState: StateMachineState,
            newError: Throwable,
            history: StaffedFlowHospital.FlowMedicalHistory
    ): StaffedFlowHospital.Diagnosis {
        return if (newError.mentionsThrowable(HospitalizeFlowException::class.java)) {
            StaffedFlowHospital.Diagnosis.OVERNIGHT_OBSERVATION
        } else {
            StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
        }
    }
}