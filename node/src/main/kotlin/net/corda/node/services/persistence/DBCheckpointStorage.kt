package net.corda.node.services.persistence

import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.nodeapi.internal.persistence.DatabaseTransactionManager
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.util.*
import java.util.stream.Stream
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

/**
 * Simple checkpoint key value storage in DB.
 */
class DBCheckpointStorage : CheckpointStorage {

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
    class DBCheckpoint(
            @Id
            @Column(name = "checkpoint_id", length = 64)
            var checkpointId: String = "",

            @Lob
            @Column(name = "checkpoint_value")
            var checkpoint: ByteArray = ByteArray(0)
    )

    override fun addCheckpoint(id: StateMachineRunId, checkpoint: SerializedBytes<Checkpoint>) {
        currentDBSession().saveOrUpdate(DBCheckpoint().apply {
            checkpointId = id.uuid.toString()
            this.checkpoint = checkpoint.bytes
        })
    }

    override fun removeCheckpoint(id: StateMachineRunId) {
        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBCheckpoint::class.java)
        val root = delete.from(DBCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBCheckpoint::checkpointId.name), id.uuid.toString()))
        session.createQuery(delete).executeUpdate()
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
