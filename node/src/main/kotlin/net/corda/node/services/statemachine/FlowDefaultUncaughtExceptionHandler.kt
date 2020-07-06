package net.corda.node.services.statemachine

import co.paralleluniverse.strands.Strand
import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.utilities.errorAndTerminate
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FlowDefaultUncaughtExceptionHandler(
    private val flowHospital: StaffedFlowHospital,
    private val checkpointStorage: CheckpointStorage,
    private val database: CordaPersistence,
    private val scheduledExecutor: ScheduledExecutorService
) : Strand.UncaughtExceptionHandler {

    private companion object {
        val log = contextLogger()
        const val RESCHEDULE_DELAY = 30L
    }

    override fun uncaughtException(fiber: Strand, throwable: Throwable) {
        val id = (fiber as FlowStateMachineImpl<*>).id
        if (throwable is VirtualMachineError) {
            errorAndTerminate(
                "Caught unrecoverable error from flow $id. Forcibly terminating the JVM, this might leave resources open, and most likely will.",
                throwable
            )
        } else {
            fiber.logger.warn("Caught exception from flow $id", throwable)
            setFlowToHospitalized(fiber, throwable)
        }
    }

    private fun setFlowToHospitalized(fiber: FlowStateMachineImpl<*>, throwable: Throwable) {
        val id = fiber.id
        if (!fiber.resultFuture.isDone) {
            fiber.transientState.let { state ->
                if (state != null) {
                    fiber.logger.warn("Forcing flow $id into overnight observation")
                    flowHospital.forceIntoOvernightObservation(state.value, listOf(throwable))
                    val hospitalizedCheckpoint = state.value.checkpoint.copy(status = Checkpoint.FlowStatus.HOSPITALIZED)
                    val hospitalizedState = state.value.copy(checkpoint = hospitalizedCheckpoint)
                    fiber.transientState = TransientReference(hospitalizedState)
                } else {
                    fiber.logger.warn("The fiber's transient state is not set, cannot force flow $id into in-memory overnight observation, status will still be updated in database")
                }
            }
            scheduledExecutor.schedule({ setFlowToHospitalizedRescheduleOnFailure(id) }, 0, TimeUnit.SECONDS)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setFlowToHospitalizedRescheduleOnFailure(id: StateMachineRunId) {
        try {
            log.debug { "Updating the status of flow $id to hospitalized after uncaught exception" }
            database.transaction { checkpointStorage.updateStatus(id, Checkpoint.FlowStatus.HOSPITALIZED) }
            log.debug { "Updated the status of flow $id to hospitalized after uncaught exception" }
        } catch (e: Exception) {
            log.info("Failed to update the status of flow $id to hospitalized after uncaught exception, rescheduling", e)
            scheduledExecutor.schedule({ setFlowToHospitalizedRescheduleOnFailure(id) }, RESCHEDULE_DELAY, TimeUnit.SECONDS)
        }
    }
}