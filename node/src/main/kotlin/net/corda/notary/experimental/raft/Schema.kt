package net.corda.notary.experimental.raft

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.transactions.PersistentUniquenessProvider

object RaftNotarySchema

object RaftNotarySchemaV1 : MappedSchema(
        schemaFamily = RaftNotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                PersistentUniquenessProvider.BaseComittedState::class.java,
                PersistentUniquenessProvider.Request::class.java,
                RaftUniquenessProvider.CommittedState::class.java,
                RaftUniquenessProvider.CommittedTransaction::class.java
        )
) {
    override val migrationResource: String?
        get() = "notary-raft.changelog-master"
}