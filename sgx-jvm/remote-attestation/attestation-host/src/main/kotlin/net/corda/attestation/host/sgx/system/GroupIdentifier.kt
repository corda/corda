package net.corda.attestation.host.sgx.system

/**
 * The Intel EPID group identifier.
 */
typealias GroupIdentifier = Int

/**
 * Get the string representation of the group identifier.
 */
fun GroupIdentifier.value(): String = Integer
        .toHexString(this)
        .padStart(8, '0')
