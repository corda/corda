package net.corda.core

import org.bouncycastle.asn1.ASN1ObjectIdentifier

/**
 * OIDs used for the Corda platform. Entries MUST NOT be removed from this file; if an OID is incorrectly assigned it
 * should be marked deprecated.
 */
object CordaOID {
    const val X509_EXTENSION_CORDA_ROLE = "1.3.6.1.4.1.50530.1.1"
}