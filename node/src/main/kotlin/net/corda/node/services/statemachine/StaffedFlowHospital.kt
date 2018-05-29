package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.loggerFor
import java.sql.SQLException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * This hospital consults "staff" to see if they can automatically diagnose and treat flows.
 */
object StaffedFlowHospital : FlowHospital {
    private val log = loggerFor<StaffedFlowHospital>()

    private val staff = listOf(DeadlockNurse, DuplicateInsertSpecialist)

    private val patients = ConcurrentHashMap<StateMachineRunId, MedicalHistory>()

    val numberOfPatients = patients.size

    class MedicalHistory {
        val records: MutableList<Record> = mutableListOf()

        sealed class Record(val suspendCount: Int) {
            class Admitted(val at: Instant, suspendCount: Int) : Record(suspendCount) {
                override fun toString() = "Admitted(at=$at, suspendCount=$suspendCount)"
            }

            class Discharged(val at: Instant, suspendCount: Int, val by: Staff, val error: Throwable) : Record(suspendCount) {
                override fun toString() = "Discharged(at=$at, suspendCount=$suspendCount, by=$by)"
            }
        }

        fun notDischargedForTheSameThingMoreThan(max: Int, by: Staff): Boolean {
            val lastAdmittanceSuspendCount = (records.last() as MedicalHistory.Record.Admitted).suspendCount
            return records.filterIsInstance(MedicalHistory.Record.Discharged::class.java).filter { it.by == by && it.suspendCount == lastAdmittanceSuspendCount }.count() <= max
        }

        override fun toString(): String = "${this.javaClass.simpleName}(records = $records)"
    }

    override fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, errors: List<Throwable>) {
        log.info("Flow ${flowFiber.id} admitted to hospital in state $currentState")
        val medicalHistory = patients.computeIfAbsent(flowFiber.id) { MedicalHistory() }
        medicalHistory.records += MedicalHistory.Record.Admitted(Instant.now(), currentState.checkpoint.numberOfSuspends)
        for ((index, error) in errors.withIndex()) {
            log.info("Flow ${flowFiber.id} has error [$index]", error)
            if (!errorIsDischarged(flowFiber, currentState, error, medicalHistory)) {
                // If any error isn't discharged, then we propagate.
                log.warn("Flow ${flowFiber.id} error was not discharged, propagating.")
                flowFiber.scheduleEvent(Event.StartErrorPropagation)
                return
            }
        }
        // If all are discharged, retry.
        flowFiber.scheduleEvent(Event.RetryFlowFromSafePoint)
    }

    private fun errorIsDischarged(flowFiber: FlowFiber, currentState: StateMachineState, error: Throwable, medicalHistory: MedicalHistory): Boolean {
        for (staffMember in staff) {
            val diagnosis = staffMember.consult(flowFiber, currentState, error, medicalHistory)
            if (diagnosis == Diagnosis.DISCHARGE) {
                medicalHistory.records += MedicalHistory.Record.Discharged(Instant.now(), currentState.checkpoint.numberOfSuspends, staffMember, error)
                log.info("Flow ${flowFiber.id} error discharged from hospital by $staffMember")
                return true
            }
        }
        return false
    }

    // It's okay for flows to be cleaned... we fix them now!
    override fun flowCleaned(flowFiber: FlowFiber) {}

    override fun flowRemoved(flowFiber: FlowFiber) {
        patients.remove(flowFiber.id)
    }

    enum class Diagnosis {
        /**
         * Retry from last safe point.
         */
        DISCHARGE,
        /**
         * Please try another member of staff.
         */
        NOT_MY_SPECIALTY
    }

    interface Staff {
        fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis
    }

    /**
     * SQL Deadlock detection.
     */
    object DeadlockNurse : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis {
            return if (mentionsDeadlock(newError)) {
                Diagnosis.DISCHARGE
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun mentionsDeadlock(exception: Throwable?): Boolean {
            return exception != null && (exception is SQLException && ((exception.message?.toLowerCase()?.contains("deadlock")
                    ?: false)) || mentionsDeadlock(exception.cause))
        }
    }

    /**
     * Primary key violation detection for duplicate inserts.  Will detect other constraint violations too.
     */
    object DuplicateInsertSpecialist : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis {
            return if (mentionsConstraintViolation(newError) && history.notDischargedForTheSameThingMoreThan(3, this)) {
                Diagnosis.DISCHARGE
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun mentionsConstraintViolation(exception: Throwable?): Boolean {
            return exception != null && (exception is org.hibernate.exception.ConstraintViolationException || mentionsConstraintViolation(exception.cause))
        }
    }
}