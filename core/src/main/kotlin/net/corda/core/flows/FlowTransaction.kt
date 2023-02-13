package net.corda.nodeapi.flow

import net.corda.core.flows.StateMachineRunId
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

    fun isInitiator(): Boolean {
        return metadata?.peers != null
    }
}

@CordaSerializable
data class FlowTransactionMetadata(
        val initiator: CordaX500Name,
        val statesToRecord: StatesToRecord,
        val peers: Set<CordaX500Name>? = null
)

@CordaSerializable
enum class TransactionStatus {
    UNVERIFIED,
    VERIFIED,
    MISSING_NOTARY_SIG;
}