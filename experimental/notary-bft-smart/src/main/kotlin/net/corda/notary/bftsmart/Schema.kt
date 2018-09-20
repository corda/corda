package net.corda.notary.bftsmart

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.notary.bftsmart.BftSmartNotaryService

object BftSmartNotary

object BftSmartNotaryV1 : MappedSchema(
        schemaFamily = BftSmartNotary.javaClass,
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