package net.corda.node.services.statemachine.hospital

import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.mentionsThrowable
import java.sql.SQLException

/**
 * SQL Deadlock detection.
 */
object DeadlockNurse : StaffedFlowHospital.Staff, StaffedFlowHospital.Chronic {
    override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: StaffedFlowHospital.FlowMedicalHistory): StaffedFlowHospital.Diagnosis {
        return if (mentionsDeadlock(newError)) {
            StaffedFlowHospital.Diagnosis.DISCHARGE
        } else {
            StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
        }
    }

    private fun mentionsDeadlock(exception: Throwable?): Boolean {
        return exception.mentionsThrowable(SQLException::class.java, "deadlock")
    }
}