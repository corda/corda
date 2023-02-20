package net.corda.core.flows

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FlowTransaction

        if (stateMachineRunId != other.stateMachineRunId) return false
        if (txId != other.txId) return false
        if (status != other.status) return false
        if (signatures != null) {
            if (other.signatures == null) return false
            if (!signatures.contentEquals(other.signatures)) return false
        } else if (other.signatures != null) return false
        if (timestamp != other.timestamp) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stateMachineRunId.hashCode()
        result = 31 * result + txId.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (signatures?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }
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
    MISSING_NOTARY_SIG;
}