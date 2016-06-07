package com.r3corda.core.contracts

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
        verifyNotary(tx)
        typeSpecificVerify(tx)
    }

    private fun verifyNotary(tx: TransactionForVerification) {
        if (tx.inStates.isEmpty()) return
        val notary = tx.inStates.first().notary
        if (tx.inStates.any { it.notary != notary }) throw TransactionVerificationException.MoreThanOneNotary(tx)
        if (tx.commands.none { it.signers.contains(notary.owningKey) }) throw TransactionVerificationException.NotaryMissing(tx)
    }

    abstract fun typeSpecificVerify(tx: TransactionForVerification)

    /** A general type used for business transactions, where transaction validity is determined by custom contract code */
    class Business : TransactionType() {
        /**
         * Check the transaction is contract-valid by running the verify() for each input and output state contract.
         * If any contract fails to verify, the whole transaction is considered to be invalid.
         */
        override fun typeSpecificVerify(tx: TransactionForVerification) {
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
    }

    /**
     * A special transaction type for reassigning a notary for a state. Validation does not involve running
     * any contract code, it just checks that the states are unmodified apart from the notary field.
     */
    class NotaryChange : TransactionType() {
        /**
         * Check that the difference between inputs and outputs is only the notary field,
         * and that all required signing public keys are present
         */
        override fun typeSpecificVerify(tx: TransactionForVerification) {
            try {
                tx.inStates.zip(tx.outStates).forEach {
                    check(it.first.data == it.second.data)
                    check(it.first.notary != it.second.notary)
                }
                val command = tx.commands.requireSingleCommand<ChangeNotary>()
                val requiredSigners = tx.inStates.flatMap { it.data.participants }
                check(command.signers.containsAll(requiredSigners))
            } catch (e: IllegalStateException) {
                throw TransactionVerificationException.InvalidNotaryChange(tx)
            }
        }
    }
}

