@file:JvmName("Verifier")
package net.corda.deterministic.verifier

import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction

/**
 * We assume the signatures were already checked outside the sandbox: the purpose of this code
 * is simply to check the sensitive, app-specific parts of a transaction.
 *
 * TODO: Transaction data is meant to be encrypted under an enclave-private key.
 */
@Throws(Exception::class)
fun verifyTransaction(reqBytes: ByteArray) {
    deserialize(reqBytes).verify()
}

private fun deserialize(reqBytes: ByteArray): LedgerTransaction {
    return reqBytes.deserialize<TransactionVerificationRequest>()
        .toLedgerTransaction()
}
