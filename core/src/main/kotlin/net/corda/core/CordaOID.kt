package net.corda.core

import net.corda.core.crypto.internal.AliasPrivateKey

/**
 * OIDs used for the Corda platform. All entries MUST be defined in this file only and they MUST NOT be removed.
 * If an OID is incorrectly assigned, it should be marked deprecated and NEVER be reused again.
 */
object CordaOID {
    /** Assigned to R3, see http://www.oid-info.com/cgi-bin/display?oid=1.3.6.1.4.1.50530&action=display */
    const val R3_ROOT = "1.3.6.1.4.1.50530"
    /** OIDs issued for the Corda platform. */
    const val CORDA_PLATFORM = "$R3_ROOT.1"
    /**
     * Identifier for the X.509 certificate extension specifying the Corda role. See
     * https://r3-cev.atlassian.net/wiki/spaces/AWG/pages/156860572/Certificate+identity+type+extension for details.
     */
    const val X509_EXTENSION_CORDA_ROLE = "$CORDA_PLATFORM.1"

    /** OID for [AliasPrivateKey]. */
    const val ALIAS_PRIVATE_KEY = "$CORDA_PLATFORM.2"
}
