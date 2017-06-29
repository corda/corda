package net.corda.core.node.services.vault

import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.lang.reflect.Field
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

@CordaSerializable
enum class BinaryLogicalOperator {
    AND,
    OR
}

@CordaSerializable
enum class EqualityComparisonOperator {
    EQUAL,
    NOT_EQUAL
}

@CordaSerializable
enum class BinaryComparisonOperator {
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
}

@CordaSerializable
enum class NullOperator {
    IS_NULL,
    NOT_NULL
}

@CordaSerializable
enum class LikenessOperator {
    LIKE,
    NOT_LIKE
}

@CordaSerializable
enum class CollectionOperator {
    IN,
    NOT_IN
}

@CordaSerializable
enum class AggregateFunctionType {
    COUNT,
    AVG,
    MIN,
    MAX,
    SUM,
}

@CordaSerializable
sealed class CriteriaExpression<O, out T> {
    data class BinaryLogical<O>(val left: CriteriaExpression<O, Boolean>, val right: CriteriaExpression<O, Boolean>, val operator: BinaryLogicalOperator) : CriteriaExpression<O, Boolean>()
    data class Not<O>(val expression: CriteriaExpression<O, Boolean>) : CriteriaExpression<O, Boolean>()
    data class ColumnPredicateExpression<O, C>(val column: Column<O, C>, val predicate: ColumnPredicate<C>) : CriteriaExpression<O, Boolean>()
    data class AggregateFunctionExpression<O, C>(val column: Column<O, C>, val predicate: ColumnPredicate<C>,
                                                 val groupByColumns: List<Column<O, C>>?) : CriteriaExpression<O, Boolean>()
}

@CordaSerializable
sealed class Column<O, out C> {
    data class Java<O, out C>(val field: Field) : Column<O, C>()
    data class Kotlin<O, out C>(val property: KProperty1<O, C?>) : Column<O, C>()
}

@CordaSerializable
sealed class ColumnPredicate<C> {
    data class EqualityComparison<C>(val operator: EqualityComparisonOperator, val rightLiteral: C) : ColumnPredicate<C>()
    data class BinaryComparison<C : Comparable<C>>(val operator: BinaryComparisonOperator, val rightLiteral: C) : ColumnPredicate<C>()
    data class Likeness(val operator: LikenessOperator, val rightLiteral: String) : ColumnPredicate<String>()
    data class CollectionExpression<C>(val operator: CollectionOperator, val rightLiteral: Collection<C>) : ColumnPredicate<C>()
    data class Between<C : Comparable<C>>(val rightFromLiteral: C, val rightToLiteral: C) : ColumnPredicate<C>()
    data class NullExpression<C>(val operator: NullOperator) : ColumnPredicate<C>()
    data class AggregateFunction<C>(val type: AggregateFunctionType) : ColumnPredicate<C>()
}

fun <O, R> resolveEnclosingObjectFromExpression(expression: CriteriaExpression<O, R>): Class<O> {
    return when (expression) {
        is CriteriaExpression.BinaryLogical -> resolveEnclosingObjectFromExpression(expression.left)
        is CriteriaExpression.Not -> resolveEnclosingObjectFromExpression(expression.expression)
        is CriteriaExpression.ColumnPredicateExpression<O, *> -> resolveEnclosingObjectFromColumn(expression.column)
        is CriteriaExpression.AggregateFunctionExpression<O, *> -> resolveEnclosingObjectFromColumn(expression.column)
    }
}

fun <O, C> resolveEnclosingObjectFromColumn(column: Column<O, C>): Class<O> {
    return when (column) {
        is Column.Java -> column.field.declaringClass as Class<O>
        is Column.Kotlin -> column.property.javaField!!.declaringClass as Class<O>
    }
}

fun <O, C> getColumnName(column: Column<O, C>): String {
    return when (column) {
        is Column.Java -> column.field.name
        is Column.Kotlin -> column.property.name
    }
}

/**
 *  Pagination and Ordering
 *
 *  Provide simple ability to specify an offset within a result set and the number of results to
 *  return from that offset (eg. page size) together with (optional) sorting criteria at column level.
 *
 *  Note: it is the responsibility of the calling client to manage page windows.
 *
 *  For advanced pagination it is recommended you utilise standard JPA query frameworks such as
 *  Spring Data's JPARepository which extends the [PagingAndSortingRepository] interface to provide
 *  paging and sorting capability:
 *  https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/repository/PagingAndSortingRepository.html
 */
val DEFAULT_PAGE_NUM = 0
val DEFAULT_PAGE_SIZE = 200

/**
 * Note: this maximum size will be configurable in future (to allow for large JVM heap sized node configurations)
 *       Use [PageSpecification] to correctly handle a number of bounded pages of [MAX_PAGE_SIZE].
 */
val MAX_PAGE_SIZE = 512

/**
 * PageSpecification allows specification of a page number (starting from 0 as default) and page size (defaulting to
 * [DEFAULT_PAGE_SIZE] with a maximum page size of [MAX_PAGE_SIZE]
 */
@CordaSerializable
data class PageSpecification(val pageNumber: Int = DEFAULT_PAGE_NUM, val pageSize: Int = DEFAULT_PAGE_SIZE)

/**
 * Sort allows specification of a set of entity attribute names and their associated directionality
 * and null handling, to be applied upon processing a query specification.
 */
@CordaSerializable
data class Sort(val columns: Collection<SortColumn>) {
    @CordaSerializable
    enum class Direction {
        ASC,
        DESC
    }

    @CordaSerializable
    interface Attribute

    enum class VaultStateAttribute(val columnName: String) : Attribute {
        /** Vault States */
        NOTARY_NAME("notaryName"),
        CONTRACT_TYPE("contractStateClassName"),
        STATE_STATUS("stateStatus"),
        RECORDED_TIME("recordedTime"),
        CONSUMED_TIME("consumedTime"),
        LOCK_ID("lockId"),
    }

    enum class LinearStateAttribute(val columnName: String) : Attribute {
        /** Vault Linear States */
        UUID("uuid"),
        EXTERNAL_ID("externalId"),
        DEAL_REFERENCE("dealReference"),
    }

    enum class FungibleStateAttribute(val columnName: String) : Attribute {
        /** Vault Fungible States */
        QUANTITY("quantity"),
        ISSUER_REF("issuerRef")
    }

    @CordaSerializable
    data class SortColumn(
        val sortAttribute: SortAttribute,
        val direction: Sort.Direction = Sort.Direction.ASC)
}

@CordaSerializable
sealed class SortAttribute {
    /**
     * [sortAttribute] refers to common table attributes defined in the node schemas:
     * VaultState, VaultLinearStates, VaultFungibleStates
     */
    data class Standard(val attribute: Sort.Attribute) : SortAttribute()

    /**
     * [entityStateClass] should reference a persistent state entity
     * [entityStateColumnName] should reference an entity attribute name as defined by the associated mapped schema
     * (for example, [CashSchemaV1.PersistentCashState::currency.name])
     */
    data class Custom(val entityStateClass: Class<out PersistentState>,
                      val entityStateColumnName: String) : SortAttribute()
}

@CordaSerializable
object Builder {

    fun <R : Comparable<R>> compare(operator: BinaryComparisonOperator, value: R) = ColumnPredicate.BinaryComparison(operator, value)

    fun <O, R> KProperty1<O, R?>.predicate(predicate: ColumnPredicate<R>) = CriteriaExpression.ColumnPredicateExpression(Column.Kotlin(this), predicate)
    fun <R> Field.predicate(predicate: ColumnPredicate<R>) = CriteriaExpression.ColumnPredicateExpression(Column.Java<Any, R>(this), predicate)

    fun <O, R> KProperty1<O, R?>.functionPredicate(predicate: ColumnPredicate<R>, groupByPredicates: List<ColumnPredicate<*>>?)
            = CriteriaExpression.AggregateFunctionExpression(Column.Kotlin(this), predicate, groupByPredicates?.map { Column.Kotlin(this) })
    fun <R> Field.functionPredicate(predicate: ColumnPredicate<R>, groupByPredicates: List<ColumnPredicate<*>>?)
            = CriteriaExpression.AggregateFunctionExpression(Column.Java<Any, R>(this), predicate, groupByPredicates?.map { Column.Java<Any, R>(this) })

    fun <O, R : Comparable<R>> KProperty1<O, R?>.comparePredicate(operator: BinaryComparisonOperator, value: R) = predicate(compare(operator, value))
    fun <R : Comparable<R>> Field.comparePredicate(operator: BinaryComparisonOperator, value: R) = predicate(compare(operator, value))

    fun <O, R> KProperty1<O, R?>.equal(value: R) = predicate(ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, value))
    fun <O, R> KProperty1<O, R?>.notEqual(value: R) = predicate(ColumnPredicate.EqualityComparison(EqualityComparisonOperator.NOT_EQUAL, value))
    fun <O, R : Comparable<R>> KProperty1<O, R?>.lessThan(value: R) = comparePredicate(BinaryComparisonOperator.LESS_THAN, value)
    fun <O, R : Comparable<R>> KProperty1<O, R?>.lessThanOrEqual(value: R) = comparePredicate(BinaryComparisonOperator.LESS_THAN_OR_EQUAL, value)
    fun <O, R : Comparable<R>> KProperty1<O, R?>.greaterThan(value: R) = comparePredicate(BinaryComparisonOperator.GREATER_THAN, value)
    fun <O, R : Comparable<R>> KProperty1<O, R?>.greaterThanOrEqual(value: R) = comparePredicate(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL, value)
    fun <O, R : Comparable<R>> KProperty1<O, R?>.between(from: R, to: R) = predicate(ColumnPredicate.Between(from, to))
    fun <O, R : Comparable<R>> KProperty1<O, R?>.`in`(collection: Collection<R>) = predicate(ColumnPredicate.CollectionExpression(CollectionOperator.IN, collection))
    fun <O, R : Comparable<R>> KProperty1<O, R?>.notIn(collection: Collection<R>) = predicate(ColumnPredicate.CollectionExpression(CollectionOperator.NOT_IN, collection))

    fun <R> Field.equal(value: R) = predicate(ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, value))
    fun <R> Field.notEqual(value: R) = predicate(ColumnPredicate.EqualityComparison(EqualityComparisonOperator.NOT_EQUAL, value))
    fun <R : Comparable<R>> Field.lessThan(value: R) = comparePredicate(BinaryComparisonOperator.LESS_THAN, value)
    fun <R : Comparable<R>> Field.lessThanOrEqual(value: R) = comparePredicate(BinaryComparisonOperator.LESS_THAN_OR_EQUAL, value)
    fun <R : Comparable<R>> Field.greaterThan(value: R) = comparePredicate(BinaryComparisonOperator.GREATER_THAN, value)
    fun <R : Comparable<R>> Field.greaterThanOrEqual(value: R) = comparePredicate(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL, value)
    fun <R : Comparable<R>> Field.between(from: R, to: R) = predicate(ColumnPredicate.Between(from, to))
    fun <R : Comparable<R>> Field.`in`(collection: Collection<R>) = predicate(ColumnPredicate.CollectionExpression(CollectionOperator.IN, collection))
    fun <R : Comparable<R>> Field.notIn(collection: Collection<R>) = predicate(ColumnPredicate.CollectionExpression(CollectionOperator.NOT_IN, collection))

    fun <R> equal(value: R) = ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, value)
    fun <R> notEqual(value: R) = ColumnPredicate.EqualityComparison(EqualityComparisonOperator.NOT_EQUAL, value)
    fun <R : Comparable<R>> lessThan(value: R) = compare(BinaryComparisonOperator.LESS_THAN, value)
    fun <R : Comparable<R>> lessThanOrEqual(value: R) = compare(BinaryComparisonOperator.LESS_THAN_OR_EQUAL, value)
    fun <R : Comparable<R>> greaterThan(value: R) = compare(BinaryComparisonOperator.GREATER_THAN, value)
    fun <R : Comparable<R>> greaterThanOrEqual(value: R) = compare(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL, value)
    fun <R : Comparable<R>> between(from: R, to: R) = ColumnPredicate.Between(from, to)
    fun <R : Comparable<R>> `in`(collection: Collection<R>) = ColumnPredicate.CollectionExpression(CollectionOperator.IN, collection)
    fun <R : Comparable<R>> notIn(collection: Collection<R>) = ColumnPredicate.CollectionExpression(CollectionOperator.NOT_IN, collection)

    fun <O> KProperty1<O, String?>.like(string: String) = predicate(ColumnPredicate.Likeness(LikenessOperator.LIKE, string))
    fun Field.like(string: String) = predicate(ColumnPredicate.Likeness(LikenessOperator.LIKE, string))
    fun <O> KProperty1<O, String?>.notLike(string: String) = predicate(ColumnPredicate.Likeness(LikenessOperator.NOT_LIKE, string))
    fun Field.notLike(string: String) = predicate(ColumnPredicate.Likeness(LikenessOperator.NOT_LIKE, string))

    fun <O, R> KProperty1<O, R?>.isNull() = predicate(ColumnPredicate.NullExpression(NullOperator.IS_NULL))
    fun Field.isNull() = predicate(ColumnPredicate.NullExpression<Any>(NullOperator.IS_NULL))
    fun <O, R> KProperty1<O, R?>.notNull() = predicate(ColumnPredicate.NullExpression(NullOperator.NOT_NULL))
    fun Field.notNull() = predicate(ColumnPredicate.NullExpression<Any>(NullOperator.NOT_NULL))

    fun <O, R> KProperty1<O, R?>.sum() = functionPredicate(ColumnPredicate.AggregateFunction(AggregateFunctionType.SUM), groupByPredicates = null)
    fun Field.sum() = functionPredicate(ColumnPredicate.AggregateFunction<Any>(AggregateFunctionType.SUM), groupByPredicates = null)

    fun <O, R> KProperty1<O, R?>.sum(vararg groupByColumns: KProperty1<O, R>) = functionPredicate(ColumnPredicate.AggregateFunction(AggregateFunctionType.SUM), groupByPredicates = null)
    fun Field.sum(groupByColumns: List<Field>) = functionPredicate(ColumnPredicate.AggregateFunction<Any>(AggregateFunctionType.SUM), groupByPredicates = null)
}

inline fun <A> builder(block: Builder.() -> A) = block(Builder)
