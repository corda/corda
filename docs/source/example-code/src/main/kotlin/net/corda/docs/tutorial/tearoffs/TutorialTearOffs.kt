@file:Suppress("UNUSED_VARIABLE")

package net.corda.docs.tutorial.tearoffs

import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.FilteredTransactionVerificationException
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.Fix
import java.util.function.Predicate

fun main(args: Array<String>) {
    // Type alias to make the example coherent.
    val oracle = Any() as AbstractParty
    val stx = Any() as SignedTransaction

    // DOCSTART 1
    val filtering = Predicate<Any> {
        when (it) {
            is Command<*> -> oracle.owningKey in it.signers && it.value is Fix
            else -> false
        }
    }
    // DOCEND 1

    // DOCSTART 2
    val ftx: FilteredTransaction = stx.buildFilteredTransaction(filtering)
    // DOCEND 2

    // DOCSTART 3
    // Direct access to included commands, inputs, outputs, attachments etc.
    val cmds: List<Command<*>> = ftx.commands
    val ins: List<StateRef> = ftx.inputs
    val timeWindow: TimeWindow? = ftx.timeWindow
    // ...
    // DOCEND 3

    try {
        ftx.verify()
    } catch (e: FilteredTransactionVerificationException) {
        throw MerkleTreeException("Rate Fix Oracle: Couldn't verify partial Merkle tree.")
    }
}
