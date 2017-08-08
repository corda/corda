package net.corda.node.services.persistence

import net.corda.core.serialization.SerializationDefaults
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.utilities.*
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
            @Column(name = "checkpoint")
            var checkpoint: ByteArray = ByteArray(0)
    )

    override fun addCheckpoint(value: Checkpoint) {
        val session = DatabaseTransactionManager.current().session
        session.save(DBCheckpoint().apply {
            checkpointId = value.id.toString()
            checkpoint = serializeToByteArray(value, SerializationDefaults.CHECKPOINT_CONTEXT)
        })
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        val session = DatabaseTransactionManager.current().session
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBCheckpoint::class.java)
        val root = delete.from(DBCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBCheckpoint::checkpointId.name), checkpoint.id.toString()))
        session.createQuery(delete).executeUpdate()
    }

    override fun forEach(block: (Checkpoint) -> Boolean) {
        val session = DatabaseTransactionManager.current().session
        val criteriaQuery = session.criteriaBuilder.createQuery(DBCheckpoint::class.java)
        val root = criteriaQuery.from(DBCheckpoint::class.java)
        criteriaQuery.select(root)
        val query = session.createQuery(criteriaQuery)
        val checkpoints = query.resultList.map { e -> deserializeFromByteArray<Checkpoint>(e.checkpoint, SerializationDefaults.CHECKPOINT_CONTEXT) }.asSequence()
        for (e in checkpoints) {
            if (!block(e)) {
                break
            }
        }
    }
}
