package net.corda.core.node.services.vault

enum class Operator {
    AND,
    OR,
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    LIKE,
    NOT_LIKE,
    BETWEEN,
    IS_NULL,
    NOT_NULL
}

interface Condition<L, R> {
    val leftOperand: L
    val operator: Operator
    val rightOperand: R
}

interface AndOr<out Q> {
    infix fun <V> and(condition: Condition<V, *>): Q
    infix fun <V> or(condition: Condition<V, *>): Q
}


interface Logical<L, R> : Condition<L, R>, AndOr<Logical<*, *>>

open class LogicalExpression<L, R>(leftOperand: L,
                              operator: Operator,
                              rightOperand: R?) : Logical<L, R> {

    override fun <V> and(condition: Condition<V, *>): Logical<*, *> = LogicalExpression(this, Operator.AND, condition)
    override fun <V> or(condition: Condition<V, *>): Logical<*, *> = LogicalExpression(this, Operator.OR, condition)

    override val operator: Operator = operator
    override val rightOperand: R = rightOperand as R
    override val leftOperand: L = leftOperand
}

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

