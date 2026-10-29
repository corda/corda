@file:JvmName("InternalVerification")
package net.corda.core.internal.verification

import net.corda.core.CordaInternal
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.FullTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

/*
 * Kotlin 2 no longer allows the internal members of another module to be accessed (not even with @Suppress("INVISIBLE_MEMBER")), so these
 * expose the internal verification functions which are needed outside of core, i.e. by Corda Enterprise and the external verifiers.
 */

/**
 * @see SignedTransaction.verifyInternal
 */
@CordaInternal
fun SignedTransaction.verifyWithSupport(verificationSupport: NodeVerificationSupport, checkSufficientSignatures: Boolean = true): FullTransaction? {
    return verifyInternal(verificationSupport, checkSufficientSignatures)
}

/**
 * @see WireTransaction.toLedgerTransactionInternal
 */
@CordaInternal
fun WireTransaction.toLedgerTransactionWithSupport(verificationSupport: VerificationSupport): LedgerTransaction {
    return toLedgerTransactionInternal(verificationSupport)
}

/**
 * @see WireTransaction.verifyInProcess
 */
@CordaInternal
fun WireTransaction.verifyInProcessWithSupport(verificationSupport: VerificationSupport): LedgerTransaction {
    return verifyInProcess(verificationSupport)
}

/**
 * @see ContractUpgradeWireTransaction.verifyInProcess
 */
@CordaInternal
fun ContractUpgradeWireTransaction.verifyInProcessWithSupport(verificationSupport: VerificationSupport) {
    verifyInProcess(verificationSupport)
}
