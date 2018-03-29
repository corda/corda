package net.corda.node.services.persistence

import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.io.Serializable
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
    ) : Serializable

    override fun addCheckpoint(checkpoint: Checkpoint) {
        currentDBSession().save(DBCheckpoint().apply {
            checkpointId = checkpoint.id.toString()
            this.checkpoint = checkpoint.serializedFiber.bytes
        })
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBCheckpoint::class.java)
        val root = delete.from(DBCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBCheckpoint::checkpointId.name), checkpoint.id.toString()))
        session.createQuery(delete).executeUpdate()
    }

    override fun forEach(block: (Checkpoint) -> Boolean) {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(DBCheckpoint::class.java)
        val root = criteriaQuery.from(DBCheckpoint::class.java)
        criteriaQuery.select(root)
        for (row in session.createQuery(criteriaQuery).resultList) {
            val checkpoint = Checkpoint(SerializedBytes(row.checkpoint))
            if (!block(checkpoint)) {
                break
            }
        }
    }
}
