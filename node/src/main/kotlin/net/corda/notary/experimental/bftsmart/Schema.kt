package net.corda.notary.experimental.bftsmart

import net.corda.core.schemas.MappedSchema
import net.corda.notary.experimental.NotaryEntities

object BFTSmartNotarySchema

object BFTSmartNotarySchemaV1 : MappedSchema(
        schemaFamily = BFTSmartNotarySchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                NotaryEntities.BaseComittedState::class.java,
                NotaryEntities.Request::class.java,
                BFTSmartNotaryService.CommittedState::class.java,
                BFTSmartNotaryService.CommittedTransaction::class.java
        )
) {
    override val migrationResource: String?
        get() = "notary-bft-smart.changelog-master"
}