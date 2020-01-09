package net.corda.ext.api.flow

import net.corda.core.flows.StateMachineRunId
import java.util.*

/**
 * Container for flows that were unable to complete normally and need special care to drive them into sensible resolution.
 */
interface FlowHospital {

    operator fun contains(flowId: StateMachineRunId): Boolean

    /**
     * Drop the errored session-init message with the given ID ([MedicalRecord.SessionInit.id]). This will cause the node
     * to send back the relevant session error to the initiator party and acknowledge its receipt from the message broker
     * so that it never gets redelivered.
     */
    fun dropSessionInit(id: UUID): Boolean

    /**
     * Forces the flow to be kept in for overnight observation by the hospital. A flow must already exist inside the hospital
     * and have existing medical records for it to be moved to overnight observation. If it does not meet these criteria then
     * an [IllegalArgumentException] will be thrown.
     *
     * @param id The [StateMachineRunId] of the flow that you are trying to force into observation
     * @param errors The errors to include in the new medical record
     */
    fun forceIntoOvernightObservation(id: StateMachineRunId, errors: List<Throwable>)

    /**
     * Remove the flow's medical history from the hospital.
     */
    fun removeMedicalHistory(flowId: StateMachineRunId)

    /**
     * Remove the flow from the hospital as it is not currently being treated.
     */
    fun leave(id: StateMachineRunId)
}