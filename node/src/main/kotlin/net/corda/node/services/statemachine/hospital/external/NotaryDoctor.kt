package net.corda.node.services.statemachine.hospital.external

import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState

// [NotaryDoctor] should be included in notary nodes only.

/**
 * Retry notarisation if the flow errors with a [NotaryError.General]. Notary flows are idempotent and only success or conflict
 * responses should be returned to the client.
 */
object NotaryDoctor : StaffedFlowHospital.Staff, StaffedFlowHospital.Chronic {
    override fun consult(flowFiber: FlowFiber,
                         currentState: StateMachineState,
                         newError: Throwable,
                         history: StaffedFlowHospital.FlowMedicalHistory): StaffedFlowHospital.Diagnosis {
        if (newError is NotaryException && newError.error is NotaryError.General) {
            return StaffedFlowHospital.Diagnosis.DISCHARGE
        }
        return StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
    }
}