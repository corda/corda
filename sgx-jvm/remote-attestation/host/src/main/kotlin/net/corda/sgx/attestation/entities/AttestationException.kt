package net.corda.sgx.attestation.entities

/**
 * Exception thrown during remote attestation.
 */
class AttestationException(
        message: String
) : Exception(message)