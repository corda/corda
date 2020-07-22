package net.corda.notary.jpa

import net.corda.core.schemas.MappedSchema

object JPANotarySchema

object JPANotarySchemaV1 : MappedSchema(
        schemaFamily = JPANotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                JPAUniquenessProvider.CommittedState::class.java,
                JPAUniquenessProvider.Request::class.java,
                JPAUniquenessProvider.CommittedTransaction::class.java
        )
) {
    override val migrationResource: String?
        get() = "node-notary.changelog-master"
}
