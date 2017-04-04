package net.corda.core.node.services.vault

import io.requery.kotlin.Logical
import io.requery.query.Condition
import io.requery.query.Operator
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria.LogicalExpression
import net.corda.core.node.services.vault.QueryCriteria.TokenType
import net.corda.core.node.services.vault.QueryCriteria.IssuedProductType
import net.corda.core.node.services.vault.QueryCriteria.TimeInstantType
import net.corda.core.serialization.OpaqueBytes
import java.time.Instant

interface QueryCriteria {

    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }

    enum class TokenType {
        CURRENCY
    }

    enum class IssuedProductType {
        CURRENCY,
        COMMODITY,
        TERMS
    }

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

open class VaultQueryCriteria(val status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
                         val stateRefs: Collection<StateRef>? = emptyList(),
                         val notary: Party? = null,
                         val includeSoftlocks: Boolean? = true,
                         val timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
                         val paging: PageSpecification? = null) : QueryCriteria {
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

/**
 * Specify any query criteria by leveraging the Requery Query DSL
 */
class VaultCustomQueryCriteria<L,R>(val expression: Logical<L,R>) : QueryCriteria

/**
 * LinearStateQueryCriteria
 */
class LinearStateQueryCriteria(val linearId: List<UniqueIdentifier>? = emptyList(),
                               val latestOnly: Boolean? = false,
                               val dealRef: Collection<String>? = emptyList(),
                               val dealParties: Collection<Party>? = emptySet(),
                               status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
                               stateRefs: Collection<StateRef>? = emptyList(),
                               notary: Party? = null,
                               includeSoftlocks: Boolean? = true,
                               timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
                               paging: PageSpecification? = null)
    : VaultQueryCriteria(status, stateRefs, notary, includeSoftlocks, timeCondition, paging)

/**
 * FungibleStateQueryCriteria
 */
class FungibleStateQueryCriteria(val owner: Party? = null,
                                 val tokenType: TokenType = TokenType.CURRENCY,
                                 val tokenValue: String? = null,
                                 val issuerParty: Party? = null,
                                 val issuerRef: OpaqueBytes,
                                 val issuedProductType: IssuedProductType? = null,
                                 val exitKeys: Collection<CompositeKey> = emptySet(),
                                 status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
                                 stateRefs: Collection<StateRef>? = emptyList(),
                                 notary: Party? = null,
                                 includeSoftlocks: Boolean? = true,
                                 timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null,
                                 paging: PageSpecification? = null)
    : VaultQueryCriteria(status, stateRefs, notary, includeSoftlocks, timeCondition, paging)

//    /**
//     * Creates a copy of the builder.
//     */
//    fun copy(): TransactionBuilder =
//            TransactionBuilder(
//                    type = type,
//                    notary = notary,
//                    inputs = ArrayList(inputs),
//                    attachments = ArrayList(attachments),
//                    outputs = ArrayList(outputs),
//                    commands = ArrayList(commands),
//                    signers = LinkedHashSet(signers),
//                    timestamp = timestamp
//            )

//    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
//    fun withItems(vararg items: Any): QueryCriteria {
//        for (t in items) {
//            when (t) {
//                is StateAndRef<*> -> addInputState(t)
//                is TransactionState<*> -> addOutputState(t)
//                is ContractState -> addOutputState(t)
//                is Command -> addCommand(t)
//                is CommandData -> throw IllegalArgumentException("You passed an instance of CommandData, but that lacks the pubkey. You need to wrap it in a Command object first.")
//                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
//            }
//        }
//        return this
//    }

//    fun timeBetween(start: Instant, end: Instant): Logical<Any, Any> = LogicalExpression(start, Operator.BETWEEN, end)
//








