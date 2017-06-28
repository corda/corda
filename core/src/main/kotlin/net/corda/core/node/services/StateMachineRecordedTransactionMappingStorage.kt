package net.corda.core.node.services

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.CordaSerializable
import rx.Observable

@CordaSerializable
data class StateMachineTransactionMapping(val stateMachineRunId: StateMachineRunId, val transactionId: SecureHash)

/**
 * This is the interface to storage storing state machine -> recorded tx mappings. Any time a transaction is recorded
 * during a flow run [addMapping] should be called.
 */
interface StateMachineRecordedTransactionMappingStorage {
    fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash)
    fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping>
}
