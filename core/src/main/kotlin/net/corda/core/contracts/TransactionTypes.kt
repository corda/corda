package net.corda.core.contracts

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder

/** Defines transaction build & validation logic for a specific transaction type */
@CordaSerializable
// TODO: remove this concept
sealed class TransactionType {
    /** A general transaction type where transaction validity is determined by custom contract code */
    object General : TransactionType() {
        /** Just uses the default [TransactionBuilder] with no special logic */
        @Deprecated("Use TransactionBuilder directly instead")
        class Builder(notary: Party?) : TransactionBuilder(General, notary)
    }
}
