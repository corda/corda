package net.corda.core.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Flow data object representing key information required for recovery.
 */

@CordaSerializable
data class FlowTransactionInfo(
        val stateMachineRunId: StateMachineRunId,
        val txId: String,
        val status: TransactionStatus,
        val timestamp: Instant,
        val metadata: TransactionMetadata?
) {
    fun isInitiator(myCordaX500Name: CordaX500Name) =
            this.metadata?.initiator == myCordaX500Name
}

@CordaSerializable
data class TransactionMetadata(
        val initiator: CordaX500Name,
        val distributionList: DistributionList? = null,
        val persist: Boolean = true     // whether to persist to transactional store
)

@CordaSerializable
class DistributionList(
    val senderStatesToRecord: StatesToRecord = StatesToRecord.NONE,
    val peersToStatesToRecord: Map<CordaX500Name, StatesToRecord>
)

@CordaSerializable
enum class TransactionStatus {
    UNVERIFIED,
    VERIFIED,
    IN_FLIGHT;
}

@CordaSerializable
data class RecoveryTimeWindow(val fromTime: Instant, val untilTime: Instant = Instant.now()) {

    init {
        if (untilTime < fromTime) {
            throw IllegalArgumentException("$fromTime must be before $untilTime")
        }
    }

    companion object {
        @JvmStatic
        fun between(fromTime: Instant, untilTime: Instant): RecoveryTimeWindow {
            return RecoveryTimeWindow(fromTime, untilTime)
        }

        @JvmStatic
        fun fromOnly(fromTime: Instant): RecoveryTimeWindow {
            return RecoveryTimeWindow(fromTime = fromTime)
        }

        @JvmStatic
        fun untilOnly(untilTime: Instant): RecoveryTimeWindow {
            return RecoveryTimeWindow(fromTime = Instant.EPOCH, untilTime = untilTime)
        }
    }
}