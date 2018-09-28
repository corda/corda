package net.corda.notary.bftsmart

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.notary.bftsmart.BftSmartNotaryService

object BftSmartNotarySchema

object BftSmartNotarySchemaV1 : MappedSchema(
        schemaFamily = BftSmartNotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                PersistentUniquenessProvider.BaseComittedState::class.java,
                PersistentUniquenessProvider.Request::class.java,
                BftSmartNotaryService.CommittedState::class.java
        )
) {
    override val migrationResource: String?
        get() = "notary-bft-smart.changelog-master"
}