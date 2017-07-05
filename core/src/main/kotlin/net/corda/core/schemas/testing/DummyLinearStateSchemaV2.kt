package net.corda.core.schemas.testing

/**
 * Second version of a cash contract ORM schema that extends the common
 * [VaultLinearState] abstract schema
 */
object DummyLinearStateSchemaV2 : net.corda.core.schemas.MappedSchema(schemaFamily = DummyLinearStateSchema.javaClass, version = 2,
        mappedTypes = listOf(net.corda.core.schemas.testing.DummyLinearStateSchemaV2.PersistentDummyLinearState::class.java)) {
    @javax.persistence.Entity
    @javax.persistence.Table(name = "dummy_linear_states_v2")
    class PersistentDummyLinearState(
            @javax.persistence.Column(name = "linear_string") var linearString: String,

            @javax.persistence.Column(name = "linear_number") var linearNumber: Long,

            @javax.persistence.Column(name = "linear_timestamp") var linearTimestamp: java.time.Instant,

            @javax.persistence.Column(name = "linear_boolean") var linearBoolean: Boolean,

            /** parent attributes */
            @Transient
            val uid: net.corda.core.contracts.UniqueIdentifier
    ) : net.corda.node.services.vault.schemas.jpa.CommonSchemaV1.LinearState(uid = uid)
}
