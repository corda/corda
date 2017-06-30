package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.internal.castIfPossible
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow]. It automates the receiving
 * and resolving of a signed transaction or input hashes.
 *
 * This flow is a combination of [receive] and [ResolveTransactionsFlow], it will expect a incoming message of type
 * [TransactionData] created by [SendTransactionFlow]. This flow will resolve the transaction data and return a
 * [UntrustworthyData] for further verification.
 */

class ReceiveTransactionFlow<out T : Any>
@JvmOverloads
constructor(private val expectedDataType: Class<T>,
            private val otherParty: Party,
            private val verifySignatures: Boolean = true,
            private val verifyTransaction: Boolean = true) : FlowLogic<UntrustworthyData<T>>() {

    @Suspendable
    @SuppressWarnings
    override fun call(): UntrustworthyData<T> {
        return receive<TransactionData<*>>(otherParty).unwrap {
            val resolveTransactionFlow = when (it) {
                is TransactionData.SignedTransactionData -> ResolveTransactionsFlow(otherParty, it.tx, verifySignatures, verifyTransaction)
                is TransactionData.TransactionHashesData -> ResolveTransactionsFlow(otherParty, it.tx)
            }
            subFlow(resolveTransactionFlow)
            UntrustworthyData(it.checkPayloadIs(expectedDataType))
        }
    }

    private fun <T> TransactionData<*>.checkPayloadIs(type: Class<T>): T {
        return extraData?.let { type.castIfPossible(it) } ?: type.castIfPossible(tx) ?:
                throw UnexpectedFlowEndException("We were expecting a ${type.name} from $otherParty but we instead got a " +
                        "${tx.javaClass.name} ($tx), ${extraData?.javaClass?.name} ($extraData)")

    }
}
