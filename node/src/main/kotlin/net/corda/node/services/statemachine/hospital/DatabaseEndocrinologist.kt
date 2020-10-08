package net.corda.node.services.statemachine.hospital

import net.corda.core.internal.VisibleForTesting
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import java.sql.SQLException
import javax.persistence.PersistenceException

/**
 * Hospitalise any database (SQL and Persistence) exception that wasn't handled otherwise, unless on the configurable whitelist
 * Note that retry decisions from other specialists will not be affected as retries take precedence over hospitalisation.
 */
object DatabaseEndocrinologist : StaffedFlowHospital.Staff {
    override fun consult(
            flowFiber: FlowFiber,
            currentState: StateMachineState,
            newError: Throwable,
            history: StaffedFlowHospital.FlowMedicalHistory
    ): StaffedFlowHospital.Diagnosis {
        return if ((newError is SQLException || newError is PersistenceException) && !customConditions.any { it(newError) }) {
            StaffedFlowHospital.Diagnosis.OVERNIGHT_OBSERVATION
        } else {
            StaffedFlowHospital.Diagnosis.NOT_MY_SPECIALTY
        }
    }

    @VisibleForTesting
    val customConditions = mutableSetOf<(t: Throwable) -> Boolean>()
}