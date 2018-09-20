package net.corda.notary.raft

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.transactions.PersistentUniquenessProvider

object RaftNotary

object RaftNotaryV1 : MappedSchema(
        schemaFamily = RaftNotary.javaClass,
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