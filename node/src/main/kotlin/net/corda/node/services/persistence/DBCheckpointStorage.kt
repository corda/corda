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

import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.debug
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Stream
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

/**
 * Simple checkpoint key value storage in DB.
 */
class DBCheckpointStorage : CheckpointStorage {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
    class DBCheckpoint(
            @Id
            @Column(name = "checkpoint_id", length = 64)
            var checkpointId: String = "",

            @Lob
            @Column(name = "checkpoint_value")
            var checkpoint: ByteArray = EMPTY_BYTE_ARRAY
    ) : Serializable

    override fun addCheckpoint(id: StateMachineRunId, checkpoint: SerializedBytes<Checkpoint>) {
        currentDBSession().saveOrUpdate(DBCheckpoint().apply {
            checkpointId = id.uuid.toString()
            this.checkpoint = checkpoint.bytes
            log.debug { "Checkpoint $checkpointId, size=${this.checkpoint.size}" }
        })
    }

    override fun removeCheckpoint(id: StateMachineRunId): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBCheckpoint::class.java)
        val root = delete.from(DBCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBCheckpoint::checkpointId.name), id.uuid.toString()))
        return session.createQuery(delete).executeUpdate() > 0
    }

    override fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, SerializedBytes<Checkpoint>>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(DBCheckpoint::class.java)
        val root = criteriaQuery.from(DBCheckpoint::class.java)
        criteriaQuery.select(root)
        return session.createQuery(criteriaQuery).stream().map {
            StateMachineRunId(UUID.fromString(it.checkpointId)) to SerializedBytes<Checkpoint>(it.checkpoint)
        }
    }
}
