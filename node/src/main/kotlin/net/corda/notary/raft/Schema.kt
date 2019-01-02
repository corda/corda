package net.corda.notary.raft

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.transactions.PersistentUniquenessProvider

object RaftNotarySchema

object RaftNotarySchemaV1 : MappedSchema(
        schemaFamily = RaftNotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                PersistentUniquenessProvider.BaseComittedState::class.java,
                PersistentUniquenessProvider.Request::class.java,
                RaftUniquenessProvider.CommittedState::class.java
        )
) {
    override val migrationResource: String?
        get() = "notary-raft.changelog-master"
}