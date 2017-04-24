package net.corda.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * An object used to fully qualify the [GenericVaultIndexSchema] family name (i.e. independent of version).
 */
object GenericVaultIndexSchema

/**
 * First version of the generic Vault Index ORM schema that provides an arbitrary set of mappable attributes
 * for use by 3rd party contracts as indexable state properties (and thus queryable using the [QueryCriteria] in the
 * vault query API functions [queryBy] and [trackBy]
 */
object GenericVaultIndexSchemaV1 : MappedSchema(schemaFamily = GenericVaultIndexSchema.javaClass, version = 1, mappedTypes = listOf(PersistentGenericVaultIndexSchemaState::class.java)) {
    @Entity
    @Table(name = "vault_indexes")
    class PersistentGenericVaultIndexSchemaState(

            /** references a concrete ContractState that is [QueryableState] and has a [MappedSchema] */
            @get:io.requery.Column(name = "contract_state_class_name")
            var contractStateClassName: String,

            /**
             * Set of re-usable custom index attributes
             */

            @Column(name = "string_index1")
            var stringIndex1: String? = null,

            @Column(name = "string_index2")
            var stringIndex2: String? = null,

            @Column(name = "string_index3")
            var stringIndex3: String? = null,

            @Column(name = "time_index1")
            var timeIndex1: Instant? = null,

            @Column(name = "time_index2")
            var timeIndex2: Instant? = null,

            @Column(name = "time_index3")
            var timeIndex3: Instant? = null,

            @Column(name = "long_index1")
            var longIndex1: Long? = null,

            @Column(name = "long_index2")
            var longIndex2: Long? = null,

            @Column(name = "long_index3")
            var longIndex3: Long? = null

    ) : PersistentState()
}
