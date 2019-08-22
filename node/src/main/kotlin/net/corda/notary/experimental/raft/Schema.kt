package net.corda.notary.experimental.raft

import net.corda.core.schemas.MappedSchema
import net.corda.notary.experimental.NotaryEntities

object RaftNotarySchema

object RaftNotarySchemaV1 : MappedSchema(
        schemaFamily = RaftNotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                NotaryEntities.BaseComittedState::class.java,
                NotaryEntities.Request::class.java,
                RaftUniquenessProvider.CommittedState::class.java,
                RaftUniquenessProvider.CommittedTransaction::class.java
        )
) {
    override val migrationResource: String?
        get() = "notary-raft.changelog-master"
}