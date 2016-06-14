package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.noneOrSingle
import java.security.PublicKey

/** Defines transaction validation rules for a specific transaction type */
sealed class TransactionType {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()

    /**
     * Check that the transaction is valid based on:
     * - General platform rules
     * - Rules for the specific transaction type
     *
     * Note: Presence of _signatures_ is not checked, only the public keys to be signed for.
     */
    fun verify(tx: TransactionForVerification) {

        val missing = verifySigners(tx)
        if (missing.isNotEmpty()) throw TransactionVerificationException.SignersMissing(tx, missing.toList())

        verifyTransaction(tx)
    }

    /** Check that the list of signers includes all the necessary keys */
    fun verifySigners(tx: TransactionForVerification): Set<PublicKey> {
        val timestamp = tx.commands.noneOrSingle { it.value is TimestampCommand }
        val timestampKey = timestamp?.signers.orEmpty()
        val notaryKey = (tx.inStates.map { it.notary.owningKey } + timestampKey).toSet()
        if (notaryKey.size > 1) throw TransactionVerificationException.MoreThanOneNotary(tx)

        val requiredKeys = getRequiredSigners(tx) + notaryKey
        val missing = requiredKeys - tx.signers

        return missing
    }

    /**
     * Return the list of public keys that that require signatures for the transaction type.
     * Note: the notary key is checked separately for all transactions and need not be included
     */
    abstract fun getRequiredSigners(tx: TransactionForVerification): Set<PublicKey>

    /** Implement type specific transaction validation logic */
    abstract fun verifyTransaction(tx: TransactionForVerification)

    /** A general transaction type where transaction validity is determined by custom contract code */
    class General : TransactionType() {
        class Builder(notary: Party? = null) : TransactionBuilder(General(), notary) {}

        /**
         * Check the transaction is contract-valid by running the verify() for each input and output state contract.
         * If any contract fails to verify, the whole transaction is considered to be invalid
         */
        override fun verifyTransaction(tx: TransactionForVerification) {
            // TODO: Check that notary is unchanged
            val ctx = tx.toTransactionForContract()

            val contracts = (ctx.inStates.map { it.contract } + ctx.outStates.map { it.contract }).toSet()
            for (contract in contracts) {
                try {
                    contract.verify(ctx)
                } catch(e: Throwable) {
                    throw TransactionVerificationException.ContractRejection(tx, contract, e)
                }
            }
        }

        override fun getRequiredSigners(tx: TransactionForVerification): Set<PublicKey> {
            val commandKeys = tx.commands.flatMap { it.signers }.toSet()
            return commandKeys
        }
    }

    /**
     * A special transaction type for reassigning a notary for a state. Validation does not involve running
     * any contract code, it just checks that the states are unmodified apart from the notary field.
     */
    class NotaryChange : TransactionType() {
        /**
         * A transaction builder that automatically sets the transaction type to [NotaryChange]
         * and adds the list of participants to the signers set for every input state.
         */
        class Builder(notary: Party? = null) : TransactionBuilder(NotaryChange(), notary) {
            override fun addInputState(stateAndRef: StateAndRef<*>) {
                signers.addAll(stateAndRef.state.data.participants)
                super.addInputState(stateAndRef)
            }
        }

        /**
         * Check that the difference between inputs and outputs is only the notary field,
         * and that all required signing public keys are present
         */
        override fun verifyTransaction(tx: TransactionForVerification) {
            try {
                tx.inStates.zip(tx.outStates).forEach {
                    check(it.first.data == it.second.data)
                    check(it.first.notary != it.second.notary)
                }
                check(tx.commands.isEmpty())
            } catch (e: IllegalStateException) {
                throw TransactionVerificationException.InvalidNotaryChange(tx)
            }
        }

        override fun getRequiredSigners(tx: TransactionForVerification): Set<PublicKey> {
            val participantKeys = tx.inStates.flatMap { it.data.participants }.toSet()
            return participantKeys
        }
    }
}