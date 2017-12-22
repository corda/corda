package net.corda.attestation.host.sgx.entities

import net.corda.attestation.host.HostException

/**
 * Exception thrown during remote attestation.
 */
class AttestationException(message: String) : HostException(message)