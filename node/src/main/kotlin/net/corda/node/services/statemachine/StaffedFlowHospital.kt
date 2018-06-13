package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.TimedFlow
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.exceptions.UnknownPeerException
import net.corda.node.services.FinalityHandler
import org.hibernate.exception.ConstraintViolationException
import rx.subjects.PublishSubject
import java.sql.SQLException
import java.time.Instant
import java.util.*

/**
 * This hospital consults "staff" to see if they can automatically diagnose and treat flows.
 */
class StaffedFlowHospital {
    private companion object {
        private val log = contextLogger()
        private val staff = listOf(DeadlockNurse, DuplicateInsertSpecialist, DoctorTimeout, FinalityDoctor, UnknownPeerAssistant)
    }

    private val mutex = ThreadBox(object {
        val patients = HashMap<StateMachineRunId, MedicalHistory>()
        val recordsPublisher = PublishSubject.create<MedicalRecord>()
    })

    class MedicalHistory {
        internal val records: MutableList<MedicalRecord> = mutableListOf()

        fun notDischargedForTheSameThingMoreThan(max: Int, by: Staff): Boolean {
            val lastAdmittanceSuspendCount = (records.last() as MedicalRecord.Admitted).suspendCount
            return records
                    .filterIsInstance<MedicalRecord.Discharged>()
                    .count { by in it.by && it.suspendCount == lastAdmittanceSuspendCount } <= max
        }

        override fun toString(): String = "${this.javaClass.simpleName}(records = $records)"
    }

    /**
     * The flow running in [flowFiber] has errored.
     */
    fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, errors: List<Throwable>) {
        log.info("Flow ${flowFiber.id} admitted to hospital in state $currentState")
        val suspendCount = currentState.checkpoint.numberOfSuspends

        val event = mutex.locked {
            val medicalHistory = patients.computeIfAbsent(flowFiber.id) { MedicalHistory() }

            val admitted = MedicalRecord.Admitted(flowFiber.id, Instant.now(), suspendCount)
            medicalHistory.records += admitted
            recordsPublisher.onNext(admitted)

            val report = consultStaff(flowFiber, currentState, errors, medicalHistory)

            val (newRecord, event) = when (report.diagnosis) {
                Diagnosis.DISCHARGE -> {
                    log.info("Flow ${flowFiber.id} error discharged from hospital by ${report.by}")
                    Pair(MedicalRecord.Discharged(flowFiber.id, Instant.now(), suspendCount, report.by, errors), Event.RetryFlowFromSafePoint)
                }
                Diagnosis.OVERNIGHT_OBSERVATION -> {
                    log.info("Flow ${flowFiber.id} error kept for overnight observation by ${report.by}")
                    // We don't schedule a next event for the flow - it will automatically retry from its checkpoint on node restart
                    Pair(MedicalRecord.KeptInForObservation(flowFiber.id, Instant.now(), suspendCount, report.by, errors), null)
                }
                Diagnosis.NOT_MY_SPECIALTY -> {
                    // None of the staff care for these errors so we let them propagate
                    log.info("Flow ${flowFiber.id} error allowed to propagate")
                    Pair(MedicalRecord.NothingWeCanDo(flowFiber.id, Instant.now(), suspendCount), Event.StartErrorPropagation)
                }
            }

            medicalHistory.records += newRecord
            recordsPublisher.onNext(newRecord)
            event
        }

        if (event != null) {
            flowFiber.scheduleEvent(event)
        }
    }

    private fun consultStaff(flowFiber: FlowFiber,
                             currentState: StateMachineState,
                             errors: List<Throwable>,
                             medicalHistory: MedicalHistory): ConsultationReport {
        return errors
                .mapIndexed { index, error ->
                    log.info("Flow ${flowFiber.id} has error [$index]", error)
                    val diagnoses: Map<Diagnosis, List<Staff>> = staff.groupBy { it.consult(flowFiber, currentState, error, medicalHistory) }
                    // We're only interested in the highest priority diagnosis for the error
                    val (diagnosis, by) = diagnoses.entries.minBy { it.key }!!
                    ConsultationReport(error, diagnosis, by)
                }
                // And we're only interested in the error with the highest priority diagnosis
                .minBy { it.diagnosis }!!
    }

    private data class ConsultationReport(val error: Throwable, val diagnosis: Diagnosis, val by: List<Staff>)

    /**
     * The flow running in [flowFiber] has cleaned, possibly as a result of a flow hospital resume.
     */
    // It's okay for flows to be cleaned... we fix them now!
    fun flowCleaned(flowFiber: FlowFiber) = Unit

    /**
     * The flow has been removed from the state machine.
     */
    fun flowRemoved(flowFiber: FlowFiber) {
        mutex.locked { patients.remove(flowFiber.id) }
    }

    // TODO MedicalRecord subtypes can expose the Staff class, something which we probably don't want when wiring this method to RPC
    /** Returns a stream of medical records as flows pass through the hospital. */
    fun track(): DataFeed<List<MedicalRecord>, MedicalRecord> {
        return mutex.locked {
            DataFeed(patients.values.flatMap { it.records }, recordsPublisher.bufferUntilSubscribed())
        }
    }

    /** Returns [true] if the flow is still affected by [error] or [false] otherwise.*/
    fun <T: Throwable> flowAffected(flowFiber: FlowFiber, errorType: Class<T>): Boolean {
        mutex.locked {
            patients[flowFiber.id]?.let {
                val record = it.records.last()
                if (record is MedicalRecord.KeptInForObservation) {
                    return record.errors.last().javaClass == errorType
                }
            }
        }
        return false
    }

    sealed class MedicalRecord {
        abstract val flowId: StateMachineRunId
        abstract val at: Instant
        abstract val suspendCount: Int

        data class Admitted(override val flowId: StateMachineRunId,
                            override val at: Instant,
                            override val suspendCount: Int) : MedicalRecord()

        data class Discharged(override val flowId: StateMachineRunId,
                              override val at: Instant,
                              override val suspendCount: Int,
                              val by: List<Staff>,
                              val errors: List<Throwable>) : MedicalRecord()

        data class KeptInForObservation(override val flowId: StateMachineRunId,
                                        override val at: Instant,
                                        override val suspendCount: Int,
                                        val by: List<Staff>,
                                        val errors: List<Throwable>) : MedicalRecord()

        data class NothingWeCanDo(override val flowId: StateMachineRunId,
                                  override val at: Instant,
                                  override val suspendCount: Int) : MedicalRecord()
    }

    /** The order of the enum values are in priority order. */
    enum class Diagnosis {
        /** Retry from last safe point. */
        DISCHARGE,
        /** Park and await intervention. */
        OVERNIGHT_OBSERVATION,
        /** Please try another member of staff. */
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
            return exception != null && (exception is ConstraintViolationException || mentionsConstraintViolation(exception.cause))
        }
    }

    /**
     * Restarts [TimedFlow], keeping track of the number of retries and making sure it does not
     * exceed the limit specified by the [FlowTimeoutException].
     */
    object DoctorTimeout : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis {
            if (newError is FlowTimeoutException) {
                if (isTimedFlow(flowFiber)) {
                    if (history.notDischargedForTheSameThingMoreThan(newError.maxRetries, this)) {
                        return Diagnosis.DISCHARGE
                    } else {
                        log.warn("\"Maximum number of retries reached for timed flow ${flowFiber.javaClass}")
                    }
                } else {
                    log.warn("\"Unable to restart flow: ${flowFiber.javaClass}, it is not timed and does not contain any timed sub-flows.")
                }
            }
            return Diagnosis.NOT_MY_SPECIALTY
        }

        private fun isTimedFlow(flowFiber: FlowFiber): Boolean {
            return flowFiber.snapshot().checkpoint.subFlowStack.any {
                TimedFlow::class.java.isAssignableFrom(it.flowClass)
            }
        }
    }

    /**
     * Parks [FinalityHandler]s for observation.
     */
    object FinalityDoctor : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis {
            return if (currentState.flowLogic is FinalityHandler) Diagnosis.OVERNIGHT_OBSERVATION else Diagnosis.NOT_MY_SPECIALTY
        }
    }

    object UnknownPeerAssistant : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: MedicalHistory): Diagnosis {
            return if (mentionsUnknownPeer(newError)) {
                Diagnosis.OVERNIGHT_OBSERVATION
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun mentionsUnknownPeer(exception: Throwable?): Boolean {
            return exception != null && (exception is UnknownPeerException || mentionsUnknownPeer((exception.cause)))
        }
    }
}
