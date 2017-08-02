package net.corda.node.services.persistence

import net.corda.core.schemas.MappedSchema
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

    object CheckpointSchema

    object CheckpointSchemaV1 : MappedSchema(schemaFamily = CheckpointSchema.javaClass, version = 1,
            mappedTypes = listOf(Checkpoint::class.java)) {

        @Entity
        @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
        class Checkpoint(
                @Id
                @Column(name = "checkpoint_id", length = 64)
                var checkpointId: String = "",

                @Lob
                @Column(name = "checkpoint")
                var checkpoint: ByteArray = ByteArray(0)
        )
    }

    override fun addCheckpoint(value: Checkpoint) {
        val session = DatabaseTransactionManager.current().session
        session.save(CheckpointSchemaV1.Checkpoint().apply {
            checkpointId = value.id.toString()
            checkpoint = serializeToByteArray(value, SerializationDefaults.CHECKPOINT_CONTEXT)
        })
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        val session = DatabaseTransactionManager.current().session
        session.createQuery("delete ${CheckpointSchemaV1.Checkpoint::class.java.name} where checkpointId = :ID")
        .setParameter("ID", checkpoint.id.toString())
        .executeUpdate()
    }

    override fun forEach(block: (Checkpoint) -> Boolean) {
        val criteriaQuery = DatabaseTransactionManager.current().session.criteriaBuilder.createQuery(CheckpointSchemaV1.Checkpoint::class.java)
        val root = criteriaQuery.from(CheckpointSchemaV1.Checkpoint::class.java)
        criteriaQuery.select(root)
        val query = DatabaseTransactionManager.current().session.createQuery(criteriaQuery)
        val checkpoints = query.resultList.map { e -> deserializeFromByteArray<Checkpoint>(e.checkpoint, SerializationDefaults.CHECKPOINT_CONTEXT) }.asSequence()
        for (e in checkpoints) {
            if (!block(e)) {
                break
            }
        }
    }
}
