package net.corda.core.node.services

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import rx.Observable

data class StateMachineTransactionMapping(val stateMachineRunId: StateMachineRunId, val transactionId: SecureHash)

/**
 * This is the interface to storage storing state machine -> recorded tx mappings. Any time a transaction is recorded
 * during a flow run [addMapping] should be called.
 */
interface StateMachineRecordedTransactionMappingStorage {
    fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash)
    fun track(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>>
}
