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