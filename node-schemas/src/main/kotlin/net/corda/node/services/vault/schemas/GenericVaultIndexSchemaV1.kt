package net.corda.node.services.vault.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
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
    /**
     * Defined for ease of use in contract state mappings and in JPA annotations below
     * (eg. see [CommercialPaper.State])
     */
    const val INDEX_STRING_1 = "stringIndex1"
    const val INDEX_STRING_2 = "stringIndex2"
    const val INDEX_STRING_3 = "stringIndex3"
    const val INDEX_NUMERIC_1 = "longIndex1"
    const val INDEX_NUMERIC_2 = "longIndex2"
    const val INDEX_NUMERIC_3 = "longIndex3"
    const val INDEX_TIME_1 = "timeIndex1"
    const val INDEX_TIME_2 = "timeIndex2"
    const val INDEX_TIME_3 = "timeIndex3"

    @Entity
    @Table(name = "vault_indexes",
            indexes = arrayOf(Index(columnList = INDEX_STRING_1),
                              Index(columnList = INDEX_STRING_2),
                              Index(columnList = INDEX_STRING_3),
                              Index(columnList = INDEX_NUMERIC_1),
                              Index(columnList = INDEX_NUMERIC_2),
                              Index(columnList = INDEX_NUMERIC_3),
                              Index(columnList = INDEX_TIME_1),
                              Index(columnList = INDEX_TIME_2),
                              Index(columnList = INDEX_TIME_3)))
    class PersistentGenericVaultIndexSchemaState(

            /** references a concrete ContractState that is [QueryableState] and has a [MappedSchema] */
            @get:io.requery.Column(name = "contract_state_class_name")
            var contractStateClassName: String,

            /**
             * Set of re-usable custom index attributes
             */

            @get:Column(name = "string_index_1")
            var stringIndex1: String? = null,

            @get:Column(name = "string_index_2")
            var stringIndex2: String? = null,

            @get:Column(name = "string_index_3")
            var stringIndex3: String? = null,

            @get:Column(name = "time_index_1")
            var timeIndex1: Instant? = null,

            @get:Column(name = "time_index_2")
            var timeIndex2: Instant? = null,

            @get:Column(name = "time_index_3")
            var timeIndex3: Instant? = null,

            @get:Column(name = "long_index_1")
            var longIndex1: Long? = null,

            @get:Column(name = "long_index_2")
            var longIndex2: Long? = null,

            @get:Column(name = "long_index_3")
            var longIndex3: Long? = null

    ) : PersistentState()
}
