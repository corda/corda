package net.corda.core.flows

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Flow data object representing key information required for recovery.
 */

@CordaSerializable
data class FlowTransaction(
        val stateMachineRunId: StateMachineRunId,
        val txId: String,
        val status: TransactionStatus,
        val signatures: ByteArray?,
        val timestamp: Instant,
        val metadata: FlowTransactionMetadata?) {

    fun isInitiator(myCordaX500Name: CordaX500Name) =
        this.metadata?.initiator == myCordaX500Name
}

@CordaSerializable
data class FlowTransactionMetadata(
        val initiator: CordaX500Name,
        val statesToRecord: StatesToRecord? = StatesToRecord.ONLY_RELEVANT,
        val peers: Set<CordaX500Name>? = null
)

@CordaSerializable
enum class TransactionStatus {
    UNVERIFIED,
    VERIFIED,
    IN_FLIGHT;
}

@CordaSerializable
data class TransactionRecoveryMetadata(
        val txId: SecureHash,
        val initiatorPartyId: Long?,    // CordaX500Name hashCode()
        val peerPartyIds: Set<Long>,    // CordaX500Name hashCode()
        val statesToRecord: StatesToRecord,
        val timestamp: Instant
)

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