package net.corda.core.node.services.vault

import net.corda.core.node.services.vault.Builder.`in`
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.Builder.greaterThan
import net.corda.core.node.services.vault.Builder.greaterThanOrEqual
import net.corda.core.node.services.vault.Builder.isNull
import net.corda.core.node.services.vault.Builder.lessThan
import net.corda.core.node.services.vault.Builder.lessThanOrEqual
import net.corda.core.node.services.vault.Builder.like
import net.corda.core.node.services.vault.Builder.notEqual
import net.corda.core.node.services.vault.Builder.notIn
import net.corda.core.node.services.vault.Builder.notLike
import net.corda.core.node.services.vault.Builder.notNull
import net.corda.core.node.services.vault.ColumnPredicate.AggregateFunction
import net.corda.core.node.services.vault.ColumnPredicate.Between
import net.corda.core.node.services.vault.ColumnPredicate.BinaryComparison
import net.corda.core.node.services.vault.ColumnPredicate.CollectionExpression
import net.corda.core.node.services.vault.ColumnPredicate.EqualityComparison
import net.corda.core.node.services.vault.ColumnPredicate.Likeness
import net.corda.core.node.services.vault.ColumnPredicate.NullExpression
import net.corda.core.node.services.vault.CriteriaExpression.ColumnPredicateExpression
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import org.junit.Test
import javax.persistence.Entity

class QueryCriteriaUtilsBuilderTest {

    /** JPA Entity class needed by `getField` */
    @Entity
    private class TestEntity(val field: String)

    /** Returns a `FieldInfo` object to work on */
    private val fieldInfo: FieldInfo get() = getField("field", TestEntity::class.java)

    /** Thrown for the `ColumnPredicate` types that have no `operator` field */
    private class ColumnPredicateHasNoOperatorFieldException : Exception("This ColumnPredicate has no operator field")

    /** Returns the `operator` for the given `ColumnPredicate` */
    private fun ColumnPredicate<out Any?>.getOperator(): Operator = when (this) {
        is AggregateFunction -> throw ColumnPredicateHasNoOperatorFieldException()
        is Between -> throw ColumnPredicateHasNoOperatorFieldException()
        is BinaryComparison<*> -> operator
        is CollectionExpression -> operator
        is EqualityComparison<*> -> operator
        is Likeness -> operator
        is NullExpression -> operator
    }

    /** Returns the `operator` for the given `ColumnPredicateExpression` */
    private fun ColumnPredicateExpression<Any, *>.getOperator(): Operator = this.predicate.getOperator()

    /** Assert that the `ColumnPredicateExpression` uses the given `Operator`. */
    private fun <T : ColumnPredicateExpression<Any, C>, C> ObjectAssert<T>.usesOperator(operator: Operator) {
        extracting {
            assertThat(it.getOperator()).isEqualTo(operator)
        }
    }

    /** Sample `String` value to pass to the predicate expression */
    private val stringValue = ""

    /** Sample `List` value to pass to the predicate expression */
    private val listValue = emptyList<String>()

    @Test(timeout = 500)
    fun `equal predicate uses EQUAL operator`() {
        assertThat(fieldInfo.equal(stringValue)).usesOperator(EqualityComparisonOperator.EQUAL)
    }

    @Test(timeout = 500)
    fun `equal predicate (exactMatch=false) uses EQUAL_IGNORE_CASE operator`() {
        assertThat(fieldInfo.equal(stringValue, exactMatch = false)).usesOperator(EqualityComparisonOperator.EQUAL_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `notEqual predicate uses NOT_EQUAL operator`() {
        assertThat(fieldInfo.notEqual(stringValue)).usesOperator(EqualityComparisonOperator.NOT_EQUAL)
    }

    @Test(timeout = 500)
    fun `notEqual predicate (exactMatch=false) uses NOT_EQUAL_IGNORE_CASE operator`() {
        assertThat(fieldInfo.notEqual(stringValue, exactMatch = false)).usesOperator(EqualityComparisonOperator.NOT_EQUAL_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `lessThan predicate uses LESS_THAN operator`() {
        assertThat(fieldInfo.lessThan(stringValue)).usesOperator(BinaryComparisonOperator.LESS_THAN)
    }

    @Test(timeout = 500)
    fun `lessThanOrEqual predicate uses LESS_THAN_OR_EQUAL operator`() {
        assertThat(fieldInfo.lessThanOrEqual(stringValue)).usesOperator(BinaryComparisonOperator.LESS_THAN_OR_EQUAL)
    }

    @Test(timeout = 500)
    fun `greaterThan predicate uses GREATER_THAN operator`() {
        assertThat(fieldInfo.greaterThan(stringValue)).usesOperator(BinaryComparisonOperator.GREATER_THAN)
    }

    @Test(timeout = 500)
    fun `greaterThanOrEqual predicate uses GREATER_THAN_OR_EQUAL operator`() {
        assertThat(fieldInfo.greaterThanOrEqual(stringValue)).usesOperator(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL)
    }

    @Test(timeout = 500)
    fun `in predicate uses IN operator`() {
        assertThat(fieldInfo.`in`(listValue)).usesOperator(CollectionOperator.IN)
    }

    @Test(timeout = 500)
    fun `in predicate (exactMatch=false) uses IN_IGNORE_CASE operator`() {
        assertThat(fieldInfo.`in`(listValue, exactMatch = false)).usesOperator(CollectionOperator.IN_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `notIn predicate uses NOT_IN operator`() {
        assertThat(fieldInfo.notIn(listValue)).usesOperator(CollectionOperator.NOT_IN)
    }

    @Test(timeout = 500)
    fun `notIn predicate (exactMatch=false) uses NOT_IN_IGNORE_CASE operator`() {
        assertThat(fieldInfo.notIn(listValue, exactMatch = false)).usesOperator(CollectionOperator.NOT_IN_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `like predicate uses LIKE operator`() {
        assertThat(fieldInfo.like(stringValue)).usesOperator(LikenessOperator.LIKE)
    }

    @Test(timeout = 500)
    fun `like predicate (exactMatch=false) uses LIKE_IGNORE_CASE operator`() {
        assertThat(fieldInfo.like(stringValue, exactMatch = false)).usesOperator(LikenessOperator.LIKE_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `notLike predicate uses NOT_LIKE operator`() {
        assertThat(fieldInfo.notLike(stringValue)).usesOperator(LikenessOperator.NOT_LIKE)
    }

    @Test(timeout = 500)
    fun `notLike predicate (exactMatch=false) uses NOT_LIKE_IGNORE_CASE operator`() {
        assertThat(fieldInfo.notLike(stringValue, exactMatch = false)).usesOperator(LikenessOperator.NOT_LIKE_IGNORE_CASE)
    }

    @Test(timeout = 500)
    fun `isNull predicate uses IS_NULL operator`() {
        assertThat(fieldInfo.isNull()).usesOperator(NullOperator.IS_NULL)
    }

    @Test(timeout = 500)
    fun `notNull predicate uses NOT_NULL operator`() {
        assertThat(fieldInfo.notNull()).usesOperator(NullOperator.NOT_NULL)
    }
}
