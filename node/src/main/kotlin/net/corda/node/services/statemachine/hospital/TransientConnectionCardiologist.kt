package net.corda.node.services.statemachine.hospital

import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.mentionsThrowable
import java.sql.SQLTransientConnectionException

/**
 * [SQLTransientConnectionException] detection that arise from failing to connect the underlying database/datasource
 */
object TransientConnectionCardiologist : StaffedFlowHospital.Staff {
    override fun consult(
            flowFiber: FlowFiber,
            currentState: StateMachineState,
            newError: Throwable,
            history: StaffedFlowHospital.FlowMedicalHistory
    ): StaffedFlowHospital.Diagnosis {
        return if (mentionsTransientConnection(newError)) {
            if (history.notDischargedForTheSameThingMoreThan(2, this, currentState)) {
                StaffedFlowHospital.Diagnosis.DISCHARGE
            } else {
                StaffedFlowHospital.Diagnosis.OVERNIGHT_OBSERVATION
            }
        } else {
            StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
        }
    }

    private fun mentionsTransientConnection(exception: Throwable?): Boolean {
        return exception.mentionsThrowable(SQLTransientConnectionException::class.java, "connection is not available")
    }
}