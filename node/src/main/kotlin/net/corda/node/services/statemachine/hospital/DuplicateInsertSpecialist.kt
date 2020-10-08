package net.corda.node.services.statemachine.hospital

import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.mentionsThrowable
import org.hibernate.exception.ConstraintViolationException

/**
 * Primary key violation detection for duplicate inserts.  Will detect other constraint violations too.
 */
object DuplicateInsertSpecialist : StaffedFlowHospital.Staff {
    override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: StaffedFlowHospital.FlowMedicalHistory): StaffedFlowHospital.Diagnosis {
        return if (newError.mentionsThrowable(ConstraintViolationException::class.java)
                && history.notDischargedForTheSameThingMoreThan(2, this, currentState)) {
            StaffedFlowHospital.Diagnosis.DISCHARGE
        } else {
            StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
        }
    }
}