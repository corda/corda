package net.corda.core.schemas

/**
 * An object used to fully qualify the [DummyDealStateSchema] family name (i.e. independent of version).
 */
object DummyDealStateSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [DummyDealState] contract state as it stood
 * at the time of writing.
 */
object DummyDealStateSchemaV1 : net.corda.core.schemas.MappedSchema(schemaFamily = net.corda.core.schemas.DummyDealStateSchema.javaClass, version = 1, mappedTypes = listOf(net.corda.core.schemas.DummyDealStateSchemaV1.PersistentDummyDealState::class.java)) {
    @javax.persistence.Entity
    @javax.persistence.Table(name = "dummy_deal_states")
    class PersistentDummyDealState(

            @javax.persistence.Column(name = "deal_reference")
            var dealReference: String,

            /** parent attributes */
            @javax.persistence.Transient
            val uid: net.corda.core.contracts.UniqueIdentifier

    ) : net.corda.node.services.vault.schemas.jpa.CommonSchemaV1.LinearState(uid = uid)
}
