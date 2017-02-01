package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

/** Defines transaction build & validation logic for a specific transaction type */
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
    fun verify(tx: LedgerTransaction) {
        require(tx.notary != null || tx.timestamp == null) { "Transactions with timestamps must be notarised." }
        val duplicates = detectDuplicateInputs(tx)
        if (duplicates.isNotEmpty()) throw TransactionVerificationException.DuplicateInputStates(tx, duplicates)
        val missing = verifySigners(tx)
        if (missing.isNotEmpty()) throw TransactionVerificationException.SignersMissing(tx, missing.toList())
        verifyTransaction(tx)
    }

    /** Check that the list of signers includes all the necessary keys */
    fun verifySigners(tx: LedgerTransaction): Set<CompositeKey> {
        val notaryKey = tx.inputs.map { it.state.notary.owningKey }.toSet()
        if (notaryKey.size > 1) throw TransactionVerificationException.MoreThanOneNotary(tx)

        val requiredKeys = getRequiredSigners(tx) + notaryKey
        val missing = requiredKeys - tx.mustSign

        return missing
    }

    /** Check that the inputs are unique. */
    private fun detectDuplicateInputs(tx: LedgerTransaction): Set<StateRef> {
        var seenInputs = emptySet<StateRef>()
        var duplicates = emptySet<StateRef>()
        tx.inputs.forEach { state ->
            if (seenInputs.contains(state.ref)) {
                duplicates += state.ref
            }
            seenInputs += state.ref
        }
        return duplicates
    }

    /**
     * Return the list of public keys that that require signatures for the transaction type.
     * Note: the notary key is checked separately for all transactions and need not be included.
     */
    abstract fun getRequiredSigners(tx: LedgerTransaction): Set<CompositeKey>

    /** Implement type specific transaction validation logic */
    abstract fun verifyTransaction(tx: LedgerTransaction)

    /** A general transaction type where transaction validity is determined by custom contract code */
    class General : TransactionType() {
        /** Just uses the default [TransactionBuilder] with no special logic */
        class Builder(notary: Party.Full?) : TransactionBuilder(General(), notary) {}

        override fun verifyTransaction(tx: LedgerTransaction) {
            verifyNoNotaryChange(tx)
            verifyEncumbrances(tx)
            verifyContracts(tx)
        }

        /**
         * Make sure the notary has stayed the same. As we can't tell how inputs and outputs connect, if there
         * are any inputs, all outputs must have the same notary.
         *
         * TODO: Is that the correct set of restrictions? May need to come back to this, see if we can be more
         *       flexible on output notaries.
         */
        private fun verifyNoNotaryChange(tx: LedgerTransaction) {
            if (tx.notary != null && tx.inputs.isNotEmpty()) {
                tx.outputs.forEach {
                    if (it.notary != tx.notary) {
                        throw TransactionVerificationException.NotaryChangeInWrongTransactionType(tx, it.notary)
                    }
                }
            }
        }

        private fun verifyEncumbrances(tx: LedgerTransaction) {
            // Validate that all encumbrances exist within the set of input states.
            val encumberedInputs = tx.inputs.filter { it.state.encumbrance != null }
            encumberedInputs.forEach { encumberedInput ->
                val encumbranceStateExists = tx.inputs.any {
                    it.ref.txhash == encumberedInput.ref.txhash && it.ref.index == encumberedInput.state.encumbrance
                }
                if (!encumbranceStateExists) {
                    throw TransactionVerificationException.TransactionMissingEncumbranceException(
                            tx, encumberedInput.state.encumbrance!!,
                            TransactionVerificationException.Direction.INPUT
                    )
                }
            }

            // Check that, in the outputs, an encumbered state does not refer to itself as the encumbrance,
            // and that the number of outputs can contain the encumbrance.
            for ((i, output) in tx.outputs.withIndex()) {
                val encumbranceIndex = output.encumbrance ?: continue
                if (encumbranceIndex == i || encumbranceIndex >= tx.outputs.size) {
                    throw TransactionVerificationException.TransactionMissingEncumbranceException(
                            tx, encumbranceIndex,
                            TransactionVerificationException.Direction.OUTPUT)
                }
            }
        }

        /**
         * Check the transaction is contract-valid by running the verify() for each input and output state contract.
         * If any contract fails to verify, the whole transaction is considered to be invalid.
         */
        private fun verifyContracts(tx: LedgerTransaction) {
            val ctx = tx.toTransactionForContract()
            // TODO: This will all be replaced in future once the sandbox and contract constraints work is done.
            val contracts = (ctx.inputs.map { it.contract } + ctx.outputs.map { it.contract }).toSet()
            for (contract in contracts) {
                try {
                    contract.verify(ctx)
                } catch(e: Throwable) {
                    throw TransactionVerificationException.ContractRejection(tx, contract, e)
                }
            }
        }

        override fun getRequiredSigners(tx: LedgerTransaction) = tx.commands.flatMap { it.signers }.toSet()
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
        class Builder(notary: Party.Full) : TransactionBuilder(NotaryChange(), notary) {
            override fun addInputState(stateAndRef: StateAndRef<*>) {
                signers.addAll(stateAndRef.state.data.participants)
                super.addInputState(stateAndRef)
            }
        }

        /**
         * Check that the difference between inputs and outputs is only the notary field, and that all required signing
         * public keys are present.
         *
         * @throws TransactionVerificationException.InvalidNotaryChange if the validity check fails.
         */
        override fun verifyTransaction(tx: LedgerTransaction) {
            try {
                for ((input, output) in tx.inputs.zip(tx.outputs)) {
                    check(input.state.data == output.data)
                    check(input.state.notary != output.notary)
                }
                check(tx.commands.isEmpty())
            } catch (e: IllegalStateException) {
                throw TransactionVerificationException.InvalidNotaryChange(tx)
            }
        }

        override fun getRequiredSigners(tx: LedgerTransaction) = tx.inputs.flatMap { it.state.data.participants }.toSet()
    }
}
