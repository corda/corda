package net.corda.core.node.services.vault

import io.requery.kotlin.Logical
import io.requery.query.Condition
import io.requery.query.Operator
import net.corda.core.contracts.Commodity
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria.AndComposition
import net.corda.core.node.services.vault.QueryCriteria.OrComposition
import net.corda.core.serialization.OpaqueBytes
import java.time.Instant
import java.util.*

/**
 * Indexing assumptions:
 * QueryCriteria assumes underlying schema tables are correctly indexed for performance.
 *
 */

sealed class QueryCriteria {

    /**
     * VaultQueryCriteria: provides query by attributes defined in [VaultSchema.VaultStates]
     */
    data class VaultQueryCriteria @JvmOverloads constructor (
            val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            val stateRefs: List<StateRef>? = null,
            val contractStateTypes: Set<Class<out ContractState>>? = null,
            val notaryName: List<String>? = null,
            val includeSoftlocks: Boolean? = true,
            val timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
            val participantIdentities: List<String>? = null) : QueryCriteria()

    /**
     * LinearStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultLinearState]
     */
    data class LinearStateQueryCriteria @JvmOverloads constructor(
            val linearId: List<UniqueIdentifier>? = null,
            val latestOnly: Boolean? = true,
            val dealRef: List<String>? = null,
            val dealPartyName: List<String>? = null) : QueryCriteria()

   /**
    * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleState]
    *
    * Valid TokenType implementations defined by Amount<T> are
    *   [Currency] as used in [Cash] contract state
    *   [Commodity] as used in [CommodityContract] state
    */
    data class FungibleAssetQueryCriteria @JvmOverloads constructor(
            val ownerIdentity: List<String>? = null,
            val quantity: Logical<*,Long>? = null,
            val tokenType: List<Class<out Any>>? = null,
            val tokenValue: List<String>? = null,
            val issuerPartyName: List<String>? = null,
            val issuerRef: List<OpaqueBytes>? = null,
            val exitKeyIdentity: List<String>? = null) : QueryCriteria()

    /**
     * VaultIndexedQueryCriteria: provides query by custom attributes defined in a contracts
     * [QueryableState] implementation. A custom state must defined its own state attribute mappings
     * to a versioned generic Vault Index schema [PersistentGenericVaultIndexSchemaState]
     * (see Persistence documentation for more information)
     *
     * Refer to [CommercialPaper.State] for a concrete example.
     */
    data class VaultIndexedQueryCriteria(val indexExpression: IndexLogical? = null) : QueryCriteria()

    interface IndexLogical : IndexCondition, AndOr<IndexLogical>

    interface IndexCondition

    interface AndOr<out Q> {
        infix fun and(condition: IndexCondition): Q
        infix fun or(condition: IndexCondition): Q
    }

    class IndexExpression(val columnName: String, val operator: Operator, val value: Any) : IndexLogical {
        override fun and(condition: IndexCondition): IndexLogical = IndexExpression(this.columnName, Operator.AND, this.value)
        override fun or(condition: IndexCondition): IndexLogical = IndexExpression(this.columnName, Operator.OR, this.value)
    }

    /**
     * Specify any query criteria by leveraging the Requery Query DSL:
     * provides query ability on any [Queryable] custom contract state attribute defined by a [MappedSchema]
     */
    data class VaultCustomQueryCriteria<L,R>(val expression: Logical<L,R>? = null) : QueryCriteria()

    // enable composition of [QueryCriteria]
    data class AndComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria()
    data class OrComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria()

    // timestamps stored in the vault states table [VaultSchema.VaultStates]
    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }

    //
    // NOTE: this class leverages Requery types: [Logical] [Condition] [Operator]
    //
    class LogicalExpression<L, R>(leftOperand: L,
                                  operator: Operator,
                                  rightOperand: R?) : Logical<L, R> {

        override fun <V> and(condition: Condition<V, *>): Logical<*, *> = LogicalExpression(this, Operator.AND, condition)
        override fun <V> or(condition: Condition<V, *>): Logical<*, *> = LogicalExpression(this, Operator.OR, condition)

        override fun getOperator(): Operator = operator
        override fun getRightOperand(): R = rightOperand
        override fun getLeftOperand(): L = leftOperand
    }
}

infix fun QueryCriteria.and(criteria: QueryCriteria): QueryCriteria = AndComposition(this, criteria)
infix fun QueryCriteria.or(criteria: QueryCriteria): QueryCriteria = OrComposition(this, criteria)

/**
 *  Provide simple ability to specify an offset within a result set and the number of results to
 *  return from that offset (eg. page size)
 *
 *  Note: it is the responsibility of the calling client to manage page windows.
 *
 *  For advanced pagination it is recommended you utilise standard JPA query frameworks such as
 *  Spring Data's JPARepository which extends the [PagingAndSortingRepository] interface to provide
 *  paging and sorting capability:
 *  https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/repository/PagingAndSortingRepository.html
 */
val DEFAULT_PAGE_NUM = 1
val DEFAULT_PAGE_SIZE = 200

data class PageSpecification(val pageNumber: Int = DEFAULT_PAGE_NUM, val pageSize: Int = DEFAULT_PAGE_SIZE)

data class Sort @JvmOverloads constructor(val direction: Sort.Direction = Sort.Direction.ASC,
                                          val nullHandling: Sort.NullHandling = Sort.NullHandling.NULLS_LAST,
                                          val order: Sort.Order? = null) {
    enum class Direction {
        ASC,
        DESC
    }
    enum class NullHandling {
        NULLS_FIRST,
        NULLS_LAST
    }
    data class Order (val direction: Sort.Direction, val property: String)
}

/**
 * [value] refers to an index mapping defined in a Contract's [Queryable] state implementation.
 * Specifically, attributes that have been mapped within the [generateMappedObject] function to generic vault
 * index attributes defined in [GenericVaultIndexSchemaV1]
 *
 * Refer to [CommercialPaper.State] for a concrete example.
 */

