package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow.Companion.tracker
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

/**
 * TWO_PHASE_FINALITY Recovery Flow (available in Enterprise only)
 */
@StartableByRPC
@InitiatingFlow
class FinalityRecoveryFlow(
        private val txIds: Collection<SecureHash> = emptySet(),
        private val flowIds: Collection<StateMachineRunId> = emptySet(),
        private val matchingCriteria: FlowRecoveryQuery? = null,
        private val forceRecover: Boolean = false,
        private val recoverAll: Boolean = false,
        override val progressTracker: ProgressTracker = ProgressTracker()) : FlowLogic<Map<FlowTransactionInfo, Boolean>>() {

    constructor(txId: SecureHash, forceRecover: Boolean = false) : this(setOf(txId), forceRecover)
    constructor(txIds: Collection<SecureHash>, forceRecover: Boolean = false, recoverAll: Boolean = false) : this(txIds, emptySet(), null, forceRecover, recoverAll, tracker())
    constructor(flowId: StateMachineRunId, forceRecover: Boolean = false) : this(emptySet(), setOf(flowId), null, forceRecover)
    constructor(flowIds: Collection<StateMachineRunId>, forceRecover: Boolean = false) : this(emptySet(), flowIds, null, forceRecover, false, tracker())
    constructor(recoverAll: Boolean, forceRecover: Boolean = false) : this(emptySet(), emptySet(), null, forceRecover, recoverAll, tracker())
    constructor(matchingCriteria: FlowRecoveryQuery, forceRecover: Boolean = false) : this(emptySet(), emptySet(), matchingCriteria, forceRecover, false, tracker())

    @Suspendable
    @Suppress("ComplexMethod")
    @Throws(FlowRecoveryException::class)
    override fun call(): Map<FlowTransactionInfo, Boolean> {
        throw NotImplementedError("Enterprise only feature")
    }
}

@CordaSerializable
class FlowRecoveryException(message: String, cause: Throwable? = null) : FlowException(message, cause) {
    constructor(txnId: SecureHash, message: String, cause: Throwable? = null) : this("Flow recovery failed for transaction $txnId: $message", cause)
}

@CordaSerializable
data class FlowRecoveryQuery(
        val timeframe: FlowTimeWindow? = null,
        val initiatedBy: CordaX500Name? = null,
        val counterParties: List<CordaX500Name>?  = null) {
    init {
        require(timeframe != null || initiatedBy != null || counterParties != null) {
            "Must specify at least one recovery criteria"
        }
    }
}

@CordaSerializable
data class FlowTimeWindow(val fromTime: Instant? = null, val untilTime: Instant? = null) {

    init {
        if (fromTime == null && untilTime == null)
            throw IllegalArgumentException("Must specify one or both of fromTime or/and untilTime")
        fromTime?.let { startTime ->
            untilTime?.let { endTime ->
                if (endTime < startTime) {
                    throw IllegalArgumentException(FlowTimeWindow::fromTime.name + " must be before or equal to " + FlowTimeWindow::untilTime.name)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun between(fromTime: Instant, untilTime: Instant): FlowTimeWindow {
            return FlowTimeWindow(fromTime, untilTime)
        }

        @JvmStatic
        fun fromOnly(fromTime: Instant): FlowTimeWindow {
            return FlowTimeWindow(fromTime = fromTime)
        }

        @JvmStatic
        fun untilOnly(untilTime: Instant): FlowTimeWindow {
            return FlowTimeWindow(untilTime = untilTime)
        }
    }
}