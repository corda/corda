package net.corda.core.node.services.vault

import io.requery.kotlin.Logical
import io.requery.query.Condition
import io.requery.query.Operator
import io.requery.query.Order
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.serialization.OpaqueBytes
import java.time.Instant

/**
 * Indexing assumptions:
 * QueryCriteria assumes underlying schema tables are correctly indexed for performance.
 *
 */

interface QueryCriteria {

    fun and(criteria: QueryCriteria): QueryCriteria

    // timestamps stored in the vault states table [VaultSchema.VaultStates]
    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }

    // Fungible asset: [Amount<T>]
    // should this be a free-form string attribute?
    enum class TokenType {
        CURRENCY,
        COMMODITY,
        OTHER
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
    data class PageSpecification(val pageNumber: Int, val pageSize: Int)
}

open class VaultQueryCriteria(val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                          // Usability question: single Enum Type value or Collection of Enums?
                          // Vault.StateState.UNCONSUMED or setOf(Vault.StateState.UNCONSUMED) (default)
                          // Vault.StateState.CONSUMED or setOf(Vault.StateState.CONSUMED)
                          // Vault.StateState.ALL or setOf(Vault.StateState.CONSUMED, Vault.StateState.UNCONSUMED)
                              val stateRefs: Collection<StateRef>? = null,
                              val contractStateTypes: Set<Class<out ContractState>>? = null,
                              val notary: Collection<Party>? = null,
                              val includeSoftlocks: Boolean? = true,
                              val timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
                              val paging: PageSpecification? = null) : QueryCriteria {

    override fun and(criteria: QueryCriteria): QueryCriteria = criteria.and(this)
}

/**
 * LinearStateQueryCriteria
 */
class LinearStateQueryCriteria(val linearId: List<UniqueIdentifier>? = null,
                               val latestOnly: Boolean? = false,
                               val dealRef: Collection<String>? = null,
                               val dealParties: Collection<Party>? = null) : QueryCriteria {

    override fun and(criteria: QueryCriteria): QueryCriteria = criteria.and(this)
}

//  NOTE: was originally using inheritance but enforces a lot of boilerplate code duplication

//                               status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
//                               stateRefs: Collection<StateRef>? = null,
//                               notary: Collection<Party>? = null,
//                               includeSoftlocks: Boolean? = true,
//                               timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
//                               paging: PageSpecification? = null)
//    : VaultQueryCriteria(status, stateRefs, notary, includeSoftlocks, timeCondition, paging)

/**
 * FungibleStateQueryCriteria
 */
class FungibleAssetQueryCriteria(val owner: Collection<Party>? = null,
                                 val quantity: Logical<*,Long>? = null,
                                 val tokenType: TokenType = TokenType.CURRENCY,
                                 val tokenValue: Collection<String>? = null,
                                 val issuerParty: Collection<Party>? = null,
                                 val issuerRef: Collection<OpaqueBytes>? = null,
                                 val exitKeys: Collection<CompositeKey>? = null) : QueryCriteria {

    override fun and(criteria: QueryCriteria): QueryCriteria = criteria.and(this)
}

//  NOTE: was originally using inheritance but enforces a lot of boilerplate code duplication

//                                 status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
//                                 stateRefs: Collection<StateRef>? = null,
//                                 notary: Collection<Party>? = null,
//                                 includeSoftlocks: Boolean? = true,
//                                 timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
//                                 paging: PageSpecification? = null)
//    : VaultQueryCriteria(status, stateRefs, notary, includeSoftlocks, timeCondition, paging)

/**
 * Specify any query criteria by leveraging the Requery Query DSL
 */
//
// NOTE: this class leverages Requery types: [Logical] [Order]
//
class VaultCustomQueryCriteria<L,R>(val expression: Logical<L,R>,
                                    val ordering: Order? = Order.ASC,
                                    val paging: PageSpecification? = null) : QueryCriteria {

    override fun and(criteria: QueryCriteria): QueryCriteria = criteria.and(this)
}





