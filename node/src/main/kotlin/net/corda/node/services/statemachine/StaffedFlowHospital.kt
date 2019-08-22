package net.corda.node.services.statemachine

import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.TimedFlow
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.node.services.FinalityHandler
import org.hibernate.exception.ConstraintViolationException
import rx.subjects.PublishSubject
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.pow

/**
 * This hospital consults "staff" to see if they can automatically diagnose and treat flows.
 */
class StaffedFlowHospital(private val flowMessaging: FlowMessaging, private val ourSenderUUID: String) {
    private companion object {
        private val log = contextLogger()
        private val staff = listOf(
                DeadlockNurse,
                DuplicateInsertSpecialist,
                DoctorTimeout,
                FinalityDoctor,
                TransientConnectionCardiologist
        )
    }

    private val mutex = ThreadBox(object {
        val flowPatients = HashMap<StateMachineRunId, FlowMedicalHistory>()
        val treatableSessionInits = HashMap<UUID, InternalSessionInitRecord>()
        val recordsPublisher = PublishSubject.create<MedicalRecord>()
    })
    private val secureRandom = newSecureRandom()

    private val delayedDischargeTimer = Timer("FlowHospitalDelayedDischargeTimer", true)
    /**
     * The node was unable to initiate the [InitialSessionMessage] from [sender].
     */
    fun sessionInitErrored(sessionMessage: InitialSessionMessage, sender: Party, event: ExternalEvent.ExternalMessageEvent, error: Throwable) {
        val time = Instant.now()
        val id = UUID.randomUUID()
        val outcome = if (error is SessionRejectException.UnknownClass) {
            // We probably don't have the CorDapp installed so let's pause the message in the hopes that the CorDapp is
            // installed on restart, at which point the message will be able proceed as normal. If not then it will need
            // to be dropped manually.
            Outcome.OVERNIGHT_OBSERVATION
        } else {
            Outcome.UNTREATABLE
        }

        val record = sessionMessage.run { MedicalRecord.SessionInit(id, time, outcome, initiatorFlowClassName, flowVersion, appName, sender, error) }
        mutex.locked {
            if (outcome != Outcome.UNTREATABLE) {
                treatableSessionInits[id] = InternalSessionInitRecord(sessionMessage, event, record)
                log.warn("$sender has sent a flow request for an unknown flow ${sessionMessage.initiatorFlowClassName}. Install the missing " +
                        "CorDapp this flow belongs to and restart.")
                log.warn("If you know it's safe to ignore this flow request then it can be deleted permanently using the killFlow RPC and " +
                        "the UUID $id (from the node shell you can run 'flow kill $id'). BE VERY CAUTIOUS OF THIS SECOND APPROACH AS THE " +
                        "REQUEST MAY CONTAIN A NOTARISED TRANSACTION THAT NEEDS TO BE RECORDED IN YOUR VAULT.")
            }
            recordsPublisher.onNext(record)
        }

        if (outcome == Outcome.UNTREATABLE) {
            sendBackError(error, sessionMessage, sender, event)
        }
    }

    private fun sendBackError(error: Throwable, sessionMessage: InitialSessionMessage, sender: Party, event: ExternalEvent.ExternalMessageEvent) {
        val message = (error as? SessionRejectException)?.message ?: "Unable to establish session"
        val payload = RejectSessionMessage(message, secureRandom.nextLong())
        val replyError = ExistingSessionMessage(sessionMessage.initiatorSessionId, payload)

        flowMessaging.sendSessionMessage(sender, replyError, SenderDeduplicationId(DeduplicationId.createRandom(secureRandom), ourSenderUUID))
        event.deduplicationHandler.afterDatabaseTransaction()
    }

    /**
     * Drop the errored session-init message with the given ID ([MedicalRecord.SessionInit.id]). This will cause the node
     * to send back the relevant session error to the initiator party and acknowledge its receipt from the message broker
     * so that it never gets redelivered.
     */
    fun dropSessionInit(id: UUID): Boolean {
        val (sessionMessage, event, publicRecord) = mutex.locked {
            treatableSessionInits.remove(id) ?: return false
        }
        log.info("Errored session-init permanently dropped: $publicRecord")
        sendBackError(publicRecord.error, sessionMessage, publicRecord.sender, event)
        return true
    }

    /**
     * The flow running in [flowFiber] has errored.
     */
    fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, errors: List<Throwable>) {
        val time = Instant.now()
        log.info("Flow ${flowFiber.id} admitted to hospital in state $currentState")

        val (event, backOffForChronicCondition) = mutex.locked {
            val medicalHistory = flowPatients.computeIfAbsent(flowFiber.id) { FlowMedicalHistory() }

            val report = consultStaff(flowFiber, currentState, errors, medicalHistory)

            val (outcome, event, backOffForChronicCondition) = when (report.diagnosis) {
                Diagnosis.DISCHARGE -> {
                    val backOff = calculateBackOffForChronicCondition(report, medicalHistory, currentState)
                    log.info("Flow error discharged from hospital (delay ${backOff.seconds}s) by ${report.by} (error was ${report.error.message})")
                    Triple(Outcome.DISCHARGE, Event.RetryFlowFromSafePoint, backOff)
                }
                Diagnosis.OVERNIGHT_OBSERVATION -> {
                    log.info("Flow error kept for overnight observation by ${report.by} (error was ${report.error.message})")
                    // We don't schedule a next event for the flow - it will automatically retry from its checkpoint on node restart
                    Triple(Outcome.OVERNIGHT_OBSERVATION, null, 0.seconds)
                }
                Diagnosis.NOT_MY_SPECIALTY -> {
                    // None of the staff care for these errors so we let them propagate
                    log.info("Flow error allowed to propagate", report.error)
                    Triple(Outcome.UNTREATABLE, Event.StartErrorPropagation, 0.seconds)
                }
            }

            val record = MedicalRecord.Flow(time, flowFiber.id, currentState.checkpoint.numberOfSuspends, errors, report.by, outcome)
            medicalHistory.records += record
            recordsPublisher.onNext(record)
            Pair(event, backOffForChronicCondition)
        }

        if (event != null) {
            if (backOffForChronicCondition.isZero) {
                flowFiber.scheduleEvent(event)
            } else {
                delayedDischargeTimer.schedule(object : TimerTask() {
                    override fun run() {
                        flowFiber.scheduleEvent(event)
                    }
                }, backOffForChronicCondition.toMillis())
            }
        }
    }

    private fun calculateBackOffForChronicCondition(report: ConsultationReport, medicalHistory: FlowMedicalHistory, currentState: StateMachineState): Duration {
        return report.by.firstOrNull { it is Chronic }?.let { chronicStaff ->
            return medicalHistory.timesDischargedForTheSameThing(chronicStaff, currentState).let {
                if (it == 0) {
                    0.seconds
                } else {
                    maxOf(10, (10 + (Math.random()) * (10 * 1.5.pow(it)) / 2).toInt()).seconds
                }
            }
        } ?: 0.seconds
    }

    private fun consultStaff(flowFiber: FlowFiber,
                             currentState: StateMachineState,
                             errors: List<Throwable>,
                             medicalHistory: FlowMedicalHistory): ConsultationReport {
        return errors
                .asSequence()
                .mapIndexed { index, error ->
                    // Rely on the logging context to print details of the flow ID.
                    log.info("Error ${index + 1} of ${errors.size}:", error)
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
     * The flow has been removed from the state machine.
     */
    fun flowRemoved(flowId: StateMachineRunId) {
        mutex.locked { flowPatients.remove(flowId) }
    }

    // TODO MedicalRecord subtypes can expose the Staff class, something which we probably don't want when wiring this method to RPC
    /** Returns a stream of medical records as flows pass through the hospital. */
    fun track(): DataFeed<List<MedicalRecord>, MedicalRecord> {
        return mutex.locked {
            val snapshot = (flowPatients.values.flatMap { it.records } + treatableSessionInits.values.map { it.publicRecord }).sortedBy { it.time }
            DataFeed(snapshot, recordsPublisher.bufferUntilSubscribed())
        }
    }

    operator fun contains(flowId: StateMachineRunId) = mutex.locked { flowId in flowPatients }

    class FlowMedicalHistory {
        internal val records: MutableList<MedicalRecord.Flow> = mutableListOf()

        fun notDischargedForTheSameThingMoreThan(max: Int, by: Staff, currentState: StateMachineState): Boolean {
            return timesDischargedForTheSameThing(by, currentState) <= max
        }

        fun timesDischargedForTheSameThing(by: Staff, currentState: StateMachineState): Int {
            val lastAdmittanceSuspendCount = currentState.checkpoint.numberOfSuspends
            return records.count { it.outcome == Outcome.DISCHARGE && by in it.by && it.suspendCount == lastAdmittanceSuspendCount }
        }

        override fun toString(): String = "${this.javaClass.simpleName}(records = $records)"
    }

    private data class InternalSessionInitRecord(val sessionMessage: InitialSessionMessage,
                                                 val event: ExternalEvent.ExternalMessageEvent,
                                                 val publicRecord: MedicalRecord.SessionInit)

    sealed class MedicalRecord {
        abstract val time: Instant
        abstract val outcome: Outcome
        abstract val errors: List<Throwable>

        /** Medical record for a flow that has errored. */
        data class Flow(override val time: Instant,
                        val flowId: StateMachineRunId,
                        val suspendCount: Int,
                        override val errors: List<Throwable>,
                        val by: List<Staff>,
                        override val outcome: Outcome) : MedicalRecord()

        /** Medical record for a session initiation that was unsuccessful. */
        data class SessionInit(val id: UUID,
                               override val time: Instant,
                               override val outcome: Outcome,
                               val initiatorFlowClassName: String,
                               val flowVersion: Int,
                               val appName: String,
                               val sender: Party,
                               val error: Throwable) : MedicalRecord() {
            override val errors: List<Throwable> get() = listOf(error)
        }
    }

    enum class Outcome { DISCHARGE, OVERNIGHT_OBSERVATION, UNTREATABLE }

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
        fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis
    }

    interface Chronic

    /**
     * SQL Deadlock detection.
     */
    object DeadlockNurse : Staff, Chronic {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis {
            return if (mentionsDeadlock(newError)) {
                Diagnosis.DISCHARGE
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun mentionsDeadlock(exception: Throwable?): Boolean {
            return exception.mentionsThrowable(SQLException::class.java, "deadlock")
        }
    }

    /**
     * Primary key violation detection for duplicate inserts.  Will detect other constraint violations too.
     */
    object DuplicateInsertSpecialist : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis {
            return if (newError.mentionsThrowable(ConstraintViolationException::class.java) && history.notDischargedForTheSameThingMoreThan(3, this, currentState)) {
                Diagnosis.DISCHARGE
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }
    }

    /**
     * Restarts [TimedFlow], keeping track of the number of retries and making sure it does not
     * exceed the limit specified by the [FlowTimeoutException].
     */
    object DoctorTimeout : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis {
            if (newError is FlowTimeoutException) {
                return Diagnosis.DISCHARGE
            }
            return Diagnosis.NOT_MY_SPECIALTY
        }
    }

    object FinalityDoctor : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis {
            return if (currentState.flowLogic is FinalityHandler || isFromReceiveFinalityFlow(newError)) {
                log.warn("Flow ${flowFiber.id} failed to be finalised. Manual intervention may be required before retrying " +
                        "the flow by re-starting the node. State machine state: $currentState", newError)
                Diagnosis.OVERNIGHT_OBSERVATION
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun isFromReceiveFinalityFlow(throwable: Throwable): Boolean {
            return throwable.stackTrace.any { it.className == ReceiveFinalityFlow::class.java.name }
        }
    }

    /**
     * [SQLTransientConnectionException] detection that arise from failing to connect the underlying database/datasource
     */
    object TransientConnectionCardiologist : Staff {
        override fun consult(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable, history: FlowMedicalHistory): Diagnosis {
            return if (mentionsTransientConnection(newError)) {
                if (history.notDischargedForTheSameThingMoreThan(2, this, currentState)) {
                    Diagnosis.DISCHARGE
                } else {
                    Diagnosis.OVERNIGHT_OBSERVATION
                }
            } else {
                Diagnosis.NOT_MY_SPECIALTY
            }
        }

        private fun mentionsTransientConnection(exception: Throwable?): Boolean {
            return exception.mentionsThrowable(SQLTransientConnectionException::class.java, "connection is not available")
        }
    }
}

private fun <T : Throwable> Throwable?.mentionsThrowable(exceptionType: Class<T>, errorMessage: String? = null): Boolean {
    if (this == null) {
        return false
    }
    val containsMessage = if (errorMessage != null) {
        message?.toLowerCase()?.contains(errorMessage) ?: false
    } else {
        true
    }
    return (exceptionType.isAssignableFrom(this::class.java) && containsMessage) || cause.mentionsThrowable(exceptionType, errorMessage)
}