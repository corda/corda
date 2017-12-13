package net.corda.core

/**
 * OIDs used for the Corda platform. Entries MUST NOT be removed from this file; if an OID is incorrectly assigned it
 * should be marked deprecated.
 */
object CordaOID {
    const val R3_ROOT = "1.3.6.1.4.1.50530"
    const val CORDA_PLATFORM = R3_ROOT + ".1"
    const val X509_EXTENSION_CORDA_ROLE = CORDA_PLATFORM + ".1"
}