/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.ConcurrentBox
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.node.services.api.StateMachineRecordedTransactionMappingStorage
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import rx.subjects.PublishSubject
import java.io.Serializable
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * Database storage of a txhash -> state machine id mapping.
 *
 * Mappings are added as transactions are persisted by [ServiceHub.recordTransaction], and never deleted.  Used in the
 * RPC API to correlate transaction creation with flows.
 */
@ThreadSafe
class DBTransactionMappingStorage : StateMachineRecordedTransactionMappingStorage {

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}transaction_mappings")
    class DBTransactionMapping(
            @Id
            @Column(name = "tx_id", length = 64)
            var txId: String = "",

            @Column(name = "state_machine_run_id", length = 36)
            var stateMachineRunId: String = ""
    ) : Serializable

    private companion object {
        fun createMap(): AppendOnlyPersistentMap<SecureHash, StateMachineRunId, DBTransactionMapping, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(SecureHash.parse(it.txId), StateMachineRunId(UUID.fromString(it.stateMachineRunId))) },
                    toPersistentEntity = { key: SecureHash, value: StateMachineRunId ->
                        DBTransactionMapping().apply {
                            txId = key.toString()
                            stateMachineRunId = value.uuid.toString()
                        }
                    },
                    persistentEntityClass = DBTransactionMapping::class.java
            )
        }
    }

    private class InnerState {
        val stateMachineTransactionMap = createMap()
        val updates: PublishSubject<StateMachineTransactionMapping> = PublishSubject.create()
    }

    private val concurrentBox = ConcurrentBox(InnerState())

    override fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash) {
        concurrentBox.concurrent {
            stateMachineTransactionMap.addWithDuplicatesAllowed(transactionId, stateMachineRunId)
            updates.bufferUntilDatabaseCommit().onNext(StateMachineTransactionMapping(stateMachineRunId, transactionId))
        }
    }

    override fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        return concurrentBox.exclusive {
            DataFeed(
                    stateMachineTransactionMap.allPersisted().map { StateMachineTransactionMapping(it.second, it.first) }.toList(),
                    updates.bufferUntilSubscribed().wrapWithDatabaseTransaction()
            )
        }
    }

}
