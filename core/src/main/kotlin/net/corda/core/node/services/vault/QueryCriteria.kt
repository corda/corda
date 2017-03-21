package net.corda.core.node.services.vault

import io.requery.kotlin.Logical
import io.requery.query.Condition
import io.requery.query.Operator
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.Party
import net.corda.core.node.services.Vault
import java.time.Instant

class QueryCriteria(val status: Vault.StateStatus? = Vault.StateStatus.UNCONSUMED,
                    val linearId: List<UniqueIdentifier>? = emptyList(),
                    val latestOnly: Boolean? = false,
                    val stateRefs: List<StateRef>? = emptyList(),
                    val dealRef: List<String>? = emptyList(),
                    val dealParties: List<Party>? = emptyList(),
                    val notary: Party? = null,
                    val includeSoftlocks: Boolean? = true,
                    val timeCondition: LogicalExpression<TimeInstantType, Array<Instant>>? = null // An arbitrary Requery condition
) {

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

    enum class TimeInstantType {
        RECORDED,
        CONSUMED
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




