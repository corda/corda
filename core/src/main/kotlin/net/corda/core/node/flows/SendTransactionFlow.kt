package net.corda.core.node.flows

import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * The [SendTransactionFlow] should be used to send a transaction to another peer that wishes to verify that transaction's
 * integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveTransactionFlow] at
 * the right point in the conversation to receive the sent transaction and perform the resolution back-and-forth required
 * to check the dependencies and download any missing attachments.
 *
 * @param otherSide the target party.
 * @param stx the [SignedTransaction] being sent to the [otherSide].
 */
open class SendTransactionFlow(otherSide: Party, stx: SignedTransaction) : DataVendingFlow(otherSide, stx)