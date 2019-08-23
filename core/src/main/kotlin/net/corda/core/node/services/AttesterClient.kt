package net.corda.core.node.services

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes

/**
 * Client API for submitting and validating request to an oracle certifying that some specific property is
 * satisfied by a transaction. The oracle will provide a proof of validity of the corresponding property in
 * the form of a [Certificate] instance that can be validated by any other party on the ledger
 *
 * As an example, a concrete instantiation of this service might provide an entry point to an SGX enclave validating
 * a transaction back-chain and generating a certificate consisting of a transaction signature and an SGX attestation
 * report.
 *
 * TODO: expand
 */
abstract class AttesterClient {
    abstract fun generateAttestationRequest(input: SignedTransaction): AttesterRequest
    abstract fun verify(txId: SecureHash, certificate: AttesterCertificate)
}

@CordaSerializable
enum class AttesterScheme {
    MOCK,
    SGX
}

@CordaSerializable
enum class AttesterServiceType(val id: Int) {
    BACKCHAIN_VALIDATOR(0),
}

@CordaSerializable
data class AttesterCertificate(
        val service: AttesterServiceType,
        val schemeId: AttesterScheme,
        val proof: OpaqueBytes,
        val assumptions: OpaqueBytes
)

@CordaSerializable
data class AttesterRequest(
        val requestType: AttesterServiceType,
        val schemeId: AttesterScheme,
        val tx: SignedTransaction,
        val payload: OpaqueBytes
)
